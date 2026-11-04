package advanced

type DescriptorLine struct {
	Segments []DescriptorSegment
}

func NewDescriptorLine() DescriptorLine {
	return DescriptorLine{}
}
