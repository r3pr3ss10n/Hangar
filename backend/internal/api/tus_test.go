package api

import (
	"context"
	"log/slog"
	"os"
	"path/filepath"
	"testing"

	"github.com/google/uuid"
	"github.com/tus/tusd/v2/pkg/filestore"
	tushandler "github.com/tus/tusd/v2/pkg/handler"

	"github.com/r3pr3ss10n/hangar/backend/internal/auth"
	dbsqlc "github.com/r3pr3ss10n/hangar/backend/internal/db/sqlc"
	"github.com/r3pr3ss10n/hangar/backend/internal/files"
	"github.com/r3pr3ss10n/hangar/backend/internal/telegram"
)

// testServer builds a Server with just the collaborators the tus hooks touch:
// a telegram Manager (for MaxFileBytes) and a files Service (for the root-folder
// ownership short-circuit). Neither reaches the DB on the paths exercised here.
func testServer() *Server {
	logger := slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError}))
	return &Server{
		telegram: telegram.NewManager(nil, telegram.Config{}, logger),
		files:    files.NewService(nil),
		logger:   logger,
	}
}

func authedCtx(t *testing.T) context.Context {
	t.Helper()
	user := dbsqlc.User{ID: uuid.New(), GenerateThumbnails: true}
	return auth.ContextWithUser(context.Background(), user)
}

// TestTusPreCreatePartialSkipsValidation verifies a partial upload (no filename,
// no folder metadata, a slice-sized body) passes pre-create — file-level checks
// are deferred to the final concat upload — and gets stamped with its owner.
func TestTusPreCreatePartialSkipsValidation(t *testing.T) {
	s := testServer()
	ctx := authedCtx(t)
	user, _ := auth.UserFromContext(ctx)

	_, changes, err := s.tusPreCreate(tushandler.HookEvent{
		Context: ctx,
		Upload: tushandler.FileInfo{
			IsPartial: true,
			Size:      512 * 1024,
			MetaData:  tushandler.MetaData{}, // partials carry no user metadata
		},
	})
	if err != nil {
		t.Fatalf("partial pre-create rejected: %v", err)
	}
	if got := changes.MetaData[tusMetaOwnerID]; got != user.ID.String() {
		t.Fatalf("owner not stamped on partial: got %q want %q", got, user.ID.String())
	}
}

// TestTusPreCreateFinalValidates verifies the final concat upload (which carries
// the real filename/folder and the summed size) runs full validation and is
// accepted for a valid in-ceiling upload into the root folder.
func TestTusPreCreateFinalValidates(t *testing.T) {
	s := testServer()
	ctx := authedCtx(t)
	user, _ := auth.UserFromContext(ctx)

	_, changes, err := s.tusPreCreate(tushandler.HookEvent{
		Context: ctx,
		Upload: tushandler.FileInfo{
			IsFinal:        true,
			PartialUploads: []string{"a", "b"},
			Size:           1024 * 1024, // tusd has summed the partials by now
			MetaData: tushandler.MetaData{
				"filename":  "movie.mkv",
				"folder_id": "root",
			},
		},
	})
	if err != nil {
		t.Fatalf("final pre-create rejected: %v", err)
	}
	if got := changes.MetaData[tusMetaOwnerID]; got != user.ID.String() {
		t.Fatalf("owner not stamped on final: got %q want %q", got, user.ID.String())
	}
	if changes.MetaData["filename"] != "movie.mkv" {
		t.Fatalf("final upload lost filename metadata")
	}
}

// TestTusPreCreateFinalRejectsOversize verifies the ceiling is enforced on the
// final concat upload using the summed size, not on the partials.
func TestTusPreCreateFinalRejectsOversize(t *testing.T) {
	s := testServer()
	ctx := authedCtx(t)

	_, _, err := s.tusPreCreate(tushandler.HookEvent{
		Context: ctx,
		Upload: tushandler.FileInfo{
			IsFinal: true,
			Size:    s.telegram.MaxFileBytes() + 1,
			MetaData: tushandler.MetaData{
				"filename":  "huge.bin",
				"folder_id": "root",
			},
		},
	})
	if err == nil {
		t.Fatal("oversize final upload was accepted")
	}
}

// TestTusPreCreateRejectsMissingFilename keeps the non-concat contract: a plain
// upload with no filename is still rejected.
func TestTusPreCreateRejectsMissingFilename(t *testing.T) {
	s := testServer()
	ctx := authedCtx(t)

	_, _, err := s.tusPreCreate(tushandler.HookEvent{
		Context: ctx,
		Upload:  tushandler.FileInfo{Size: 1024, MetaData: tushandler.MetaData{}},
	})
	if err == nil {
		t.Fatal("upload without filename was accepted")
	}
}

// TestTusPreFinishPartialIsNoop verifies a partial upload completing does not
// stream into Telegram or create a file row — it returns cleanly so concatenation
// can proceed. (s.telegram has no linked client; if the hook tried to upload it
// would error, so a nil error proves the early return fired.)
func TestTusPreFinishPartialIsNoop(t *testing.T) {
	s := testServer()
	store := filestore.New(t.TempDir())

	resp, err := s.tusPreFinish(tushandler.HookEvent{
		Context: context.Background(),
		Upload:  tushandler.FileInfo{ID: "partial-1", IsPartial: true, Size: 512 * 1024},
	}, store)
	if err != nil {
		t.Fatalf("partial finish returned error: %v", err)
	}
	if len(resp.Header) != 0 {
		t.Fatalf("partial finish set response headers: %v", resp.Header)
	}
}

// TestTusRemovePartials verifies the partial temp files are deleted after a
// concat assembles them.
func TestTusRemovePartials(t *testing.T) {
	dir := t.TempDir()
	store := filestore.New(dir)
	ctx := context.Background()

	// Create two partial uploads in the store and write a byte so the .bin exists.
	var ids []string
	for i := 0; i < 2; i++ {
		up, err := store.NewUpload(ctx, tushandler.FileInfo{Size: 1, IsPartial: true})
		if err != nil {
			t.Fatalf("create partial: %v", err)
		}
		info, _ := up.GetInfo(ctx)
		ids = append(ids, info.ID)
	}
	// Sanity: the .info files exist before cleanup.
	for _, id := range ids {
		if _, err := os.Stat(filepath.Join(dir, id+".info")); err != nil {
			t.Fatalf("expected partial %s to exist before cleanup: %v", id, err)
		}
	}

	s := testServer()
	s.tusRemovePartials(ctx, store, ids)

	for _, id := range ids {
		if _, err := os.Stat(filepath.Join(dir, id+".info")); !os.IsNotExist(err) {
			t.Fatalf("partial %s not removed (stat err=%v)", id, err)
		}
	}
}
