package components

type InstructionItem struct {
	Number      string
	Title       string
	Description string
}

func NewInstructionItem(number, title, description string) InstructionItem {
	return InstructionItem{
		Number:      number,
		Title:       title,
		Description: description,
	}
}
