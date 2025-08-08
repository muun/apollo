package model

type FeeWindowJson struct {
	Id int64 `json:"id"`
	// TODO: Using time.Time is ok when we unmarshal FetchDate,
	//  but we need to test that the marshaling also works.
	FetchDate string `json:"fetchDate"`
	// TODO: Check if we are ok with using "string" instead of "int" as key for TargetedFees.
	//  If not, a custom mapping should be created.
	TargetedFees     map[string]float64 `json:"targetedFees"`
	FastConfTarget   int                `json:"fastConfTarget"`
	MediumConfTarget int                `json:"mediumConfTarget"`
	SlowConfTarget   int                `json:"slowConfTarget"`
}
