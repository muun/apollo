package newop

const (
	FeeStateFinalFee      string = "FinalFee"
	FeeStateNeedsChange   string = "NeedsChange"
	FeeStateNoPossibleFee string = "NoPossibleFee"
)

type FeeState struct {
	State              string
	Amount             *BitcoinAmount
	RateInSatsPerVByte float64
	TargetBlocks       int64 // 0 if target not found
}

func (f *FeeState) IsFinal() bool {
	return f.State == FeeStateFinalFee
}

func (f *FeeState) NeedsChange() bool {
	return f.State == FeeStateNeedsChange
}

func (f *FeeState) IsNoPossibleFee() bool {
	return f.State == FeeStateNoPossibleFee
}
