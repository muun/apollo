package libwallet

import (
	"reflect"
	"testing"
)

var (
	rootPath  = &derivationPath{make([]derivationIndex, 0)}
	shortPath = &derivationPath{[]derivationIndex{
		derivationIndex{i: 0, hardened: true},
	}}
	longPath = &derivationPath{[]derivationIndex{
		derivationIndex{i: 44, hardened: true},
		derivationIndex{i: 1, hardened: true},
		derivationIndex{i: 2, hardened: false},
	}}
	shortMuunPath = &derivationPath{[]derivationIndex{
		derivationIndex{i: 1, hardened: true, name: "schema"},
	}}
	longMuunPath = &derivationPath{[]derivationIndex{
		derivationIndex{i: 1, hardened: true, name: "schema"},
		derivationIndex{i: 1, hardened: true, name: "recovery"},
	}}
)

func Test_parseDerivationPath(t *testing.T) {

	type args struct {
		path string
	}
	tests := []struct {
		name    string
		args    args
		want    *derivationPath
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
			got, err := parseDerivationPath(tt.args.path)
			if (err != nil) != tt.wantErr {
				t.Errorf("parseDerivationPath() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !reflect.DeepEqual(got, tt.want) {
				t.Errorf("parseDerivationPath() = %v, want %v", got, tt.want)
			}
		})
	}
}
