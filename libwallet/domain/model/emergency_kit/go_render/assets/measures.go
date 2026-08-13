package assets

import (
	"github.com/muun/libwallet/data/emergency_kit/resources"
)

// Standard padding used throughout the emergency kit
var StandardHorizontalMargin = resources.Mm(16)

// BodyParagraphLineHeight
// Line height are the real size each line requires considering its bottom and top space when they
// are multiline. It dependes on how big the font is, but it goes from fontSize*1.25 to fontSize*1.75
// In the css all these values are explicit.
var BodyParagraphLineHeight = resources.Mm(24)

var OutputDescriptorsLineHeight = resources.Mm(23)

// Section title line height for 24pt medium text
var SectionTitleLineHeight = resources.Mm(36)

// Subtitle line height for 20pt medium text
var SubtitleLineHeight = resources.Mm(32)

// Keys section title line height for 32pt medium text
var KeysSectionTitleLineHeight = resources.Mm(42.5)

var EncryptedKeysTextFontSize = resources.Mm(25.9)

// Spacing between related elements within a component (e.g., title to description)
var IntraComponentSpacing = resources.Mm(11)

// Standard icon size for all icons
var PadlockIconSize = resources.Mm(76)

var HelpIconSize = resources.Mm(68)
