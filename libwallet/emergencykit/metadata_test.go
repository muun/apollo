package emergencykit

import (
	"encoding/hex"
	"io/ioutil"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
)

var someMetadata = Metadata{
	Version:       1,
	BirthdayBlock: 12345,

	EncryptedKeys: []*MetadataKey{
		&MetadataKey{
			DhPubKey:         "0338c52ecbb886ab45de31120c76888da73437e3d6e81510f56d3746399f0fef52",
			EncryptedPrivKey: "d0a801c1923663295892e9a9a0bfc770abcb00c20e7cef28e2d743c96b441e677c875e8d6495afb8362aba886ae9ee346c62e82758f5b5ba9a70f61957529255",
			Salt:             "d579c14c61365bc0",
		},
	},

	OutputDescriptors: []string{
		"sh(wsh(multi(2, 89a1749c/1'/1'/0/*, 77e21d45/1'/1'/0/*)))#0wp4hp36",
	},
}

func TestReadWriteMetadata(t *testing.T) {
	// Create a temporary directory and pick some suitable paths for our input/output files:
	tmpDir := createTmpDir(t)
	srcFile := filepath.Join(tmpDir, "src.pdf")
	dstFile := filepath.Join(tmpDir, "src.pdf")

	defer os.RemoveAll(tmpDir)

	// Save the sample PDF (included at the end of this file, for readability):
	createPdfFile(t, srcFile)

	// Write metadata:
	mw := MetadataWriter{
		SrcFile: srcFile,
		DstFile: dstFile,
	}

	mw.WriteMetadata(&someMetadata)

	// Read metadata:
	mr := MetadataReader{
		SrcFile: dstFile,
	}

	metadata, err := mr.ReadMetadata()
	if err != nil {
		t.Fatalf("Failed to read metadata from %s: %v", dstFile, err)
	}

	// Verify that we got the original metadata back:
	if !reflect.DeepEqual(&someMetadata, metadata) {
		t.Fatalf("Metadata objects don't match: %v (%v vs %v)", err, someMetadata, metadata)
	}
}

func createTmpDir(t *testing.T) string {
	tmpDir, err := ioutil.TempDir("", "pdf")
	if err != nil {
		t.Fatalf("Failed to create temporary directory %s: %v", tmpDir, err)
	}

	return tmpDir
}

func createPdfFile(t *testing.T, path string) {
	content, err := hex.DecodeString(strings.Join(strings.Fields(verySmallPdf), ""))
	if err != nil {
		t.Fatalf("Failed to hex-decode the sample PDF data: %v", err)
	}

	err = ioutil.WriteFile(path, content, os.FileMode(0600))
	if err != nil {
		t.Fatalf("Failed to write PDF to %s: %v", path, err)
	}
}

// A very small valid PDF obtained by printing `<html></html>` with Chromium:
const verySmallPdf = `
	255044462d312e340a25d3ebe9e10a312030206f626a0a3c3c2f43726561
	746f7220284d6f7a696c6c612f352e30205c284d6163696e746f73683b20
	496e74656c204d6163204f5320582031305f31345f365c29204170706c65
	5765624b69742f3533372e3336205c284b48544d4c2c206c696b65204765
	636b6f5c29204368726f6d652f38372e302e343238302e38382053616661
	72692f3533372e3336290a2f50726f64756365722028536b69612f504446
	206d3837290a2f4372656174696f6e446174652028443a32303230313231
	313136333033332b303027303027290a2f4d6f64446174652028443a3230
	3230313231313136333033332b303027303027293e3e0a656e646f626a0a
	332030206f626a0a3c3c2f636120310a2f424d202f4e6f726d616c3e3e0a
	656e646f626a0a342030206f626a0a3c3c2f46696c746572202f466c6174
	654465636f64650a2f4c656e6774682039353e3e2073747265616d0a789c
	d33332b60403050320d4d543e29a5b1a2924e772157281648c4c4d0d148c
	8d0d0c148a52b9c2b514f280e2c67a8646a6607d08165083a1020806b92b
	401845e95cfaeec60ae9c560732c0ccd140c0d4ccd40c6a471050221009d
	2a19fb0a656e6473747265616d0a656e646f626a0a322030206f626a0a3c
	3c2f54797065202f506167650a2f5265736f7572636573203c3c2f50726f
	63536574205b2f504446202f54657874202f496d61676542202f496d6167
	6543202f496d616765495d0a2f457874475374617465203c3c2f47332033
	203020523e3e3e3e0a2f4d65646961426f78205b30203020363132203739
	325d0a2f436f6e74656e74732034203020520a2f53747275637450617265
	6e747320300a2f506172656e742035203020523e3e0a656e646f626a0a35
	2030206f626a0a3c3c2f54797065202f50616765730a2f436f756e742031
	0a2f4b696473205b32203020525d3e3e0a656e646f626a0a362030206f62
	6a0a3c3c2f54797065202f436174616c6f670a2f50616765732035203020
	523e3e0a656e646f626a0a787265660a3020370a30303030303030303030
	2036353533352066200a30303030303030303135203030303030206e200a
	30303030303030343731203030303030206e200a30303030303030323730
	203030303030206e200a30303030303030333037203030303030206e200a
	30303030303030363539203030303030206e200a30303030303030373134
	203030303030206e200a747261696c65720a3c3c2f53697a6520370a2f52
	6f6f742036203020520a2f496e666f2031203020523e3e0a737461727478
	7265660a3736310a2525454f46
`
