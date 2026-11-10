package model

type Batch struct{}

type batchItem struct{} // want `model files hold a single struct definition: move batchItem to batch_item.go`

type BatchSummary struct{} // want `model files hold a single struct definition: move BatchSummary to batch_summary.go`
