package advanced

type SegmentType int

const (
	SegmentDefault SegmentType = iota
	SegmentFunction
	SegmentFingerprint
	SegmentChecksum
)

type DescriptorSegment struct {
	Text string
	Type SegmentType
}

func NewDescriptorSegment(text string, segmentType SegmentType) DescriptorSegment {
	return DescriptorSegment{Text: text, Type: segmentType}
}
