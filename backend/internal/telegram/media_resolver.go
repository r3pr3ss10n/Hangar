package telegram

import (
	"context"

	"github.com/gotd/td/telegram/dcs"
	"github.com/gotd/td/transport"
)

// mediaResolver wraps a base dcs.Resolver so that Primary() connects to a DC's
// media_only endpoints when they exist, falling back to the regular endpoints
// otherwise.
//
// Why this exists: Telegram serves file bytes from dedicated "media cluster"
// servers (the DC options flagged media_only — e.g. 149.154.167.35 for DC2),
// which are distinct from the regular DC endpoint (149.154.167.41/.50). The
// official clients download from the media_only servers, which are not subject
// to the aggressive per-connection throughput limiting the regular endpoint
// applies. Our download pool was hammering the regular endpoint and capping at
// ~5-7 MB/s regardless of connection count.
//
// gotd's own Client.MediaOnly() targets these servers but insists on exporting
// authorization to the DC, which Telegram rejects with DC_ID_INVALID for the
// account's own home DC (the common case for our storage channel). By instead
// routing Primary() to the media_only endpoints, Client.Pool() reuses the
// existing primary auth key with no export and still lands on the fast servers.
type mediaResolver struct {
	base dcs.Resolver
}

func newMediaResolver(base dcs.Resolver) dcs.Resolver {
	if base == nil {
		base = dcs.DefaultResolver()
	}
	return mediaResolver{base: base}
}

// hasMediaOnly reports whether the config lists any media_only endpoint for dc.
func hasMediaOnly(list dcs.List, dc int) bool {
	for _, o := range list.Options {
		if o.ID == dc && o.MediaOnly {
			return true
		}
	}
	return false
}

func (m mediaResolver) Primary(ctx context.Context, dc int, list dcs.List) (transport.Conn, error) {
	if hasMediaOnly(list, dc) {
		if conn, err := m.base.MediaOnly(ctx, dc, list); err == nil {
			return conn, nil
		}
		// Fall back to the regular endpoint if the media dial fails.
	}
	return m.base.Primary(ctx, dc, list)
}

func (m mediaResolver) MediaOnly(ctx context.Context, dc int, list dcs.List) (transport.Conn, error) {
	return m.base.MediaOnly(ctx, dc, list)
}

func (m mediaResolver) CDN(ctx context.Context, dc int, list dcs.List) (transport.Conn, error) {
	return m.base.CDN(ctx, dc, list)
}
