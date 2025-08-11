package hdpath

import (
	"reflect"
	"testing"
)

var (
	rootPath  = make([]PathIndex, 0)
	shortPath = []PathIndex{
		PathIndex{Index: 0, Hardened: true},
	}
	longPath = []PathIndex{
		PathIndex{Index: 44, Hardened: true},
		PathIndex{Index: 1, Hardened: true},
		PathIndex{Index: 2, Hardened: false},
	}
	shortMuunPath = []PathIndex{
		PathIndex{Index: 1, Hardened: true, Name: "schema"},
	}
	longMuunPath = []PathIndex{
		PathIndex{Index: 1, Hardened: true, Name: "schema"},
		PathIndex{Index: 1, Hardened: true, Name: "recovery"},
	}
)

func TestBuild(t *testing.T) {
	p, err := Parse("m/1/1")
	if err != nil {
		t.Fatal(err)
	}
	p = p.Child(0)
	p = p.NamedChild("foo", 1)

	if p.String() != "m/1/1/0/foo:1" {
		t.Fatalf("expected path to be m/1/1/0/foo:1, got %s instead", p.String())
	}
}

func TestParsingAndValidation(t *testing.T) {

	type args struct {
		path string
	}
	tests := []struct {
		name    string
		args    args
		want    []PathIndex
		wantErr bool
	}{
		{name: "root1", args: args{path: ""}, want: rootPath},
		{name: "root2", args: args{path: "m"}, want: rootPath},
		{name: "root3", args: args{path: "/"}, want: rootPath},
		{name: "short1", args: args{path: "m/0'"}, want: shortPath},
		{name: "short2", args: args{path: "0'"}, want: shortPath},
		{name: "short3", args: args{path: "/0'"}, want: shortPath},
		{name: "long1", args: args{path: "m/44'/1'/2"}, want: longPath},
		{name: "long2", args: args{path: "/44'/1'/2"}, want: longPath},
		{name: "long3", args: args{path: "44'/1'/2"}, want: longPath},
		{name: "shortMuun", args: args{path: "m/schema:1'"}, want: shortMuunPath},
		{name: "longMuun", args: args{path: "m/schema:1'/recovery:1'"}, want: longMuunPath},
		{name: "has spaces", args: args{path: "m / 0 / 1"}, wantErr: true},
		{name: "has no indexes", args: args{path: "m/b/c"}, wantErr: true},
		{name: "has weird chars", args: args{path: "m/1.2^3"}, wantErr: true},
		{name: "has several :", args: args{path: "m/recovery:1:1"}, wantErr: true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			path, err := Parse(tt.args.path)
			if (err != nil) != tt.wantErr {
				t.Errorf("Parse() error = %v, wantErr %v", err, tt.wantErr)
			}
			if tt.wantErr {
				return
			}
			indexes := path.Indexes()
			if !reflect.DeepEqual(indexes, tt.want) {
				t.Errorf("Indexes() = %v, want %v", indexes, tt.want)
			}
		})
	}
}

func TestPrefixRecognition(t *testing.T) {
	type args struct {
		path   string
		prefix string
	}
	tests := []struct {
		name string
		args args
		want bool
	}{
		{name: "empty prefix on empty path", args: args{path: "", prefix: ""}, want: true},
		{name: "empty prefix on non-empty path", args: args{path: "m", prefix: ""}, want: true},
		{name: "long prefix on empty path", args: args{path: "", prefix: "m/schema:1'/recovery:1'"}, want: false},
		{name: "long prefix on short path", args: args{path: "m/schema:1'", prefix: "m/schema:1'/recovery:1'"}, want: false},
		{name: "same prefix and path", args: args{path: "m/0'/1'/4", prefix: "m/0'/1'/4"}, want: true},
		{name: "mismatched prefix at start", args: args{path: "m/44'/1'/2", prefix: "m/45'/1'/2"}, want: false},
		{name: "mismatched prefix at end", args: args{path: "m/44'/1'/2", prefix: "m/44'/1'/5"}, want: false},
		{name: "comments in path and prefix", args: args{path: "m/schema:1'/recovery:1'", prefix: "m/schema:1'"}, want: true},
		{name: "comments in path, not in prefix", args: args{path: "m/schema:1'/recovery:1'", prefix: "m/1'"}, want: true},
		{name: "comments in prefix, not in path", args: args{path: "m/1'/1'", prefix: "m/schema:1'"}, want: true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			prefix, err := Parse(tt.args.prefix)
			if err != nil {
				t.Fatalf("unexpected Parse() error = %v", err)
			}
			path, err := Parse(tt.args.path)
			if err != nil {
				t.Fatalf("unexpected Parse() error = %v", err)
			}

			if path.HasPrefix(prefix) != tt.want {
				t.Errorf("path.HasPrefix() = %v, with path=%v, prefix=%v", !tt.want, tt.args.path, tt.args.prefix)
			}
		})
	}
}

func TestHDPathRejectsImplicitlyHardenedPath(t *testing.T) {
	// This test is built weird cause the call we want to test doesn't return an error but instead
	// panics. We _can_ "catch" a panic via this defer-recover construction. If the call doesn't
	// panic, `t.Fatalf()` fails the test
	defer func() {
		recover()
	}()

	indexes := MustParse("m/2147483648").Indexes()
	t.Fatalf("expected panic, got value %v", indexes)
}
