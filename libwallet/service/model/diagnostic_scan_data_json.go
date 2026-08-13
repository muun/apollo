package model

type DiagnosticScanDataJson struct { //nolint:staticcheck // TODO: type DiagnosticScanDataJson should be DiagnosticScanDataJSON
	ScanId string `json:"scanId"` //nolint:staticcheck // TODO: struct field ScanId should be ScanID
	Logs   string `json:"logs"`
}
