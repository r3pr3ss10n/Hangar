package files

import (
	"testing"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"

	dbsqlc "github.com/r3pr3ss10n/hangar/backend/internal/db/sqlc"
)

func TestOwnsFolder(t *testing.T) {
	owner := uuid.New()
	other := uuid.New()

	assert.True(t, ownsFolder(owner, dbsqlc.Folder{OwnerID: owner}),
		"the owner of a folder owns it")
	assert.False(t, ownsFolder(other, dbsqlc.Folder{OwnerID: owner}),
		"a different user does not own the folder")
}

func TestOwnsFile(t *testing.T) {
	owner := uuid.New()
	other := uuid.New()

	assert.True(t, ownsFile(owner, dbsqlc.File{OwnerID: owner}),
		"the owner of a file owns it")
	assert.False(t, ownsFile(other, dbsqlc.File{OwnerID: owner}),
		"a different user does not own the file")
}

func TestPathTo(t *testing.T) {
	root := uuid.New()
	child := uuid.New()
	grandchild := uuid.New()

	byID := map[uuid.UUID]dbsqlc.Folder{
		root:       {ID: root, Name: "Root", ParentID: nil},
		child:      {ID: child, Name: "Child", ParentID: &root},
		grandchild: {ID: grandchild, Name: "Grandchild", ParentID: &child},
	}

	// nil parent (drive root) -> empty path.
	assert.Empty(t, pathTo(byID, nil), "an item at the root has no ancestors")

	// An item whose immediate parent is the root resolves to just [Root].
	got := pathTo(byID, &root)
	assert.Equal(t, []PathSegment{{ID: root, Name: "Root"}}, got)

	// pathTo includes the immediate parent and every ancestor, root-first.
	got = pathTo(byID, &grandchild)
	assert.Equal(t, []PathSegment{
		{ID: root, Name: "Root"},
		{ID: child, Name: "Child"},
		{ID: grandchild, Name: "Grandchild"},
	}, got)

	// A dangling parent (ancestor missing from the map) stops the walk cleanly.
	missing := uuid.New()
	assert.Empty(t, pathTo(byID, &missing), "a missing ancestor yields no path, not a panic")

	// A cycle (self-parenting) terminates instead of looping forever.
	loop := uuid.New()
	cyclic := map[uuid.UUID]dbsqlc.Folder{loop: {ID: loop, Name: "Loop", ParentID: &loop}}
	assert.Equal(t, []PathSegment{{ID: loop, Name: "Loop"}}, pathTo(cyclic, &loop))
}
