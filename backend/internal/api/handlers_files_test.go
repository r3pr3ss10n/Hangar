package api

import "testing"

func TestParseRange(t *testing.T) {
	const size = 1000
	cases := []struct {
		name      string
		header    string
		size      int64
		wantStart int64
		wantEnd   int64
		wantPart  bool
		wantOK    bool
	}{
		{"no header full extent", "", size, 0, size - 1, false, true},
		{"open ended", "bytes=0-", size, 0, size - 1, true, true},
		{"closed range", "bytes=100-199", size, 100, 199, true, true},
		{"to end explicit", "bytes=500-999", size, 500, 999, true, true},
		{"end past size clamps", "bytes=900-5000", size, 900, size - 1, true, true},
		{"suffix last N", "bytes=-100", size, 900, 999, true, true},
		{"suffix larger than size clamps", "bytes=-5000", size, 0, 999, true, true},
		{"first byte", "bytes=0-0", size, 0, 0, true, true},
		{"multi range honours first", "bytes=0-99,200-299", size, 0, 99, true, true},
		{"start past end of file", "bytes=1000-1100", size, 0, 0, false, false},
		{"start greater than end", "bytes=500-400", size, 0, 0, false, false},
		{"garbage prefix", "items=0-100", size, 0, 0, false, false},
		{"non numeric", "bytes=abc-def", size, 0, 0, false, false},
		{"empty file no header", "", 0, 0, -1, false, true},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			start, end, partial, ok := parseRange(c.header, c.size)
			if ok != c.wantOK {
				t.Fatalf("ok = %v, want %v", ok, c.wantOK)
			}
			if !ok {
				return
			}
			if start != c.wantStart || end != c.wantEnd {
				t.Fatalf("range = [%d,%d], want [%d,%d]", start, end, c.wantStart, c.wantEnd)
			}
			if partial != c.wantPart {
				t.Fatalf("partial = %v, want %v", partial, c.wantPart)
			}
		})
	}
}

func TestSanitizeASCIIFilename(t *testing.T) {
	cases := map[string]string{
		"simple.txt":     "simple.txt",
		"with space.pdf": "with space.pdf",
		"quote\"name":    "quote_name",
		"back\\slash":    "back_slash",
		"":               "download",
		"naïve.txt":      "na_ve.txt",
	}
	for in, want := range cases {
		if got := sanitizeASCIIFilename(in); got != want {
			t.Errorf("sanitizeASCIIFilename(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestContentDisposition(t *testing.T) {
	got := contentDisposition("naïve file.txt")
	want := `attachment; filename="na_ve file.txt"; filename*=UTF-8''na%C3%AFve%20file.txt`
	if got != want {
		t.Fatalf("contentDisposition = %q, want %q", got, want)
	}
}
