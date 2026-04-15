package go_render

import (
	"fmt"
	"github.com/muun/libwallet/data/emergency_kit"
	"github.com/muun/libwallet/data/emergency_kit/resources"
	"github.com/muun/libwallet/domain/model/emergency_kit/go_render/assets"
	"github.com/muun/libwallet/domain/model/emergency_kit/go_render/components"
	"github.com/muun/libwallet/domain/model/emergency_kit/go_render/components/advanced"
	"github.com/muun/libwallet/domain/model/emergency_kit/go_render/components/keys"
	"github.com/muun/libwallet/emergencykit"
)

// GeneratedEKPDF is a model including the path in which Libwallet left the generated pdf, the verificationCode and
// the version
type GeneratedEKPDF struct {
	Path             string
	VerificationCode string
	Version          int
}

func Render(
	ekInput *emergencykit.Input,
	expectedFilePath string,
	lang string,
) (*GeneratedEKPDF, error) {
	verificationCode := emergencykit.GenerateDeterministicCode(ekInput)

	translations, err := loadTranslations(lang)
	if err != nil {
		return nil, fmt.Errorf("failed to load translations: %w", err)
	}

	fmt.Println("Creating PDF with custom page size...")

	ctx := emergency_kit.RenderingContext{
		NonDrawableHorizontalMargins: assets.StandardHorizontalMargin,
		Images: []emergency_kit.ImageAsset{
			{Name: assets.PadlockImageName, Format: "png", Data: assets.PadlockPNG},
			{Name: assets.HelpImageName, Format: "png", Data: assets.HelpPNG},
		},
		TextStyling: emergency_kit.TextStyling{
			SetBodyFont:  assets.SetBodyParagraphFont,
			SetBodyColor: assets.SetSecondaryTextColor,
			SetLinkFont:  assets.SetBodyParagraphFontUnderlined,
			SetLinkColor: assets.SetLinkColor,
		},
	}
	pdfExt := emergency_kit.CreateAndSetupPdf(ctx)

	pdfExt.AddPage()
	pdfExt.SetXY(0, 0)

	// Render sections:
	components.NewHeaderComponent(pdfExt, verificationCode, translations).Render()

	pdfExt.SetY(pdfExt.GetY() + resources.Mm(25.5))

	keys.NewKeysComponent(
		pdfExt,
		ekInput.FirstEncryptedKey,
		ekInput.SecondEncryptedKey,
		translations,
	).Render()

	pdfExt.AddComponentSeparator()

	components.NewInstructionsComponent(pdfExt, translations).Render()

	pdfExt.AddComponentSeparator()

	components.NewHelpComponent(pdfExt, translations).Render()

	pdfExt.AddPage()
	pdfExt.SetXY(0, 0)

	descriptorsData := &emergencykit.DescriptorsData{
		FirstFingerprint:  ekInput.FirstFingerprint,
		SecondFingerprint: ekInput.SecondFingerprint,
	}
	descriptors := emergencykit.GetDescriptors(descriptorsData)

	advanced.NewAdvancedComponent(pdfExt, descriptors, translations).Render()

	// Save PDF to file
	err = pdfExt.OutputFileAndClose(expectedFilePath)
	if err != nil {
		return nil, fmt.Errorf("failed to save PDF: %w", err)
	}

	generatedEKit := &GeneratedEKPDF{
		Path:             expectedFilePath,
		VerificationCode: verificationCode,
		Version:          ekInput.Version,
	}
	return generatedEKit, nil
}

func loadTranslations(lang string) (*assets.Translations, error) {
	language := assets.English
	if lang == "es" {
		language = assets.Spanish
	}

	return assets.LoadTranslations(language)
}
