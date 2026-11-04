package go_render

import (
	"runtime"
	"time"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/legacy/emergencykit"
	"github.com/muun/libwallet/subsystems/emergency_kit/internal/data"
	"github.com/muun/libwallet/subsystems/emergency_kit/internal/data/resources"
	"github.com/muun/libwallet/subsystems/emergency_kit/internal/domain/model/go_render/assets"
	"github.com/muun/libwallet/subsystems/emergency_kit/internal/domain/model/go_render/components"
	"github.com/muun/libwallet/subsystems/emergency_kit/internal/domain/model/go_render/components/advanced"
	"github.com/muun/libwallet/subsystems/emergency_kit/internal/domain/model/go_render/components/keys"
	ek_model "github.com/muun/libwallet/subsystems/emergency_kit/presentation/model"
)

// GeneratedEKPDF is a model including the path in which Libwallet left the generated pdf, the
// verificationCode and the version
type GeneratedEKPDF struct {
	Path             string
	VerificationCode string
	Version          int
	Profiling        *ek_model.RenderProfiling
}

func NewGeneratedEKPDF(path, verificationCode string, version int) *GeneratedEKPDF {
	return &GeneratedEKPDF{
		Path:             path,
		VerificationCode: verificationCode,
		Version:          version,
	}
}

func Render(
	ekInput *emergencykit.Input,
	expectedFilePath string,
	lang string,
) (*GeneratedEKPDF, error) {
	var memBefore runtime.MemStats
	runtime.ReadMemStats(&memBefore)

	verificationCode := emergencykit.GenerateDeterministicCode(ekInput)

	startLocalizables := time.Now()
	translations, err := loadTranslations(lang)
	if err != nil {
		return nil, errors.Errorf("failed to load translations: %w", err)
	}
	loadTranslationsMs := time.Since(startLocalizables).Milliseconds()

	ctx := data.RenderingContext{
		NonDrawableHorizontalMargins: assets.StandardHorizontalMargin,
		Images: []data.ImageAsset{
			{Name: assets.PadlockImageName, Format: "jpg", Data: assets.PadlockJPEG},
			{Name: assets.HelpImageName, Format: "jpg", Data: assets.HelpJPEG},
		},
		TextStyling: data.TextStyling{
			SetBodyFont:  assets.SetBodyParagraphFont,
			SetBodyColor: assets.SetSecondaryTextColor,
			SetLinkFont:  assets.SetBodyParagraphFontUnderlined,
			SetLinkColor: assets.SetLinkColor,
		},
	}
	pdfExt := data.CreateAndSetupPdf(ctx)
	registerFontsMs := pdfExt.RegisterFontsMs
	registerImagesMs := pdfExt.RegisterImagesMs

	startDraw := time.Now()
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
	componentsRenderingMs := time.Since(startDraw).Milliseconds()

	// Save PDF to file
	startOutput := time.Now()
	err = pdfExt.OutputFileAndClose(expectedFilePath)
	if err != nil {
		return nil, errors.Errorf("failed to save PDF: %w", err)
	}
	createAndSaveOnDiskMs := time.Since(startOutput).Milliseconds()

	var memAfter runtime.MemStats
	runtime.ReadMemStats(&memAfter)

	result := NewGeneratedEKPDF(expectedFilePath, verificationCode, ekInput.Version)
	result.Profiling = NewRenderProfiling(
		loadTranslationsMs,
		registerFontsMs,
		registerImagesMs,
		componentsRenderingMs,
		createAndSaveOnDiskMs,
		// TotalAlloc/Mallocs only grow, so the delta is never negative.
		int64(memAfter.TotalAlloc-memBefore.TotalAlloc),
		int64(memAfter.Mallocs-memBefore.Mallocs),
	)
	result.Profiling.DrawIconsMs = pdfExt.DrawIconsDuration.Milliseconds()
	return result, nil
}

func loadTranslations(lang string) (*assets.Translations, error) {
	language := assets.English
	if lang == "es" {
		language = assets.Spanish
	}

	return assets.LoadTranslations(language)
}

func NewRenderProfiling(
	loadTranslationsMs int64,
	registerFontsMs int64,
	registerImagesMs int64,
	componentsRenderingMs int64,
	createAndSaveOnDiskMs int64,
	totalHeapAllocatedBytes int64,
	totalObjectsAllocated int64,
) *ek_model.RenderProfiling {
	return &ek_model.RenderProfiling{
		LoadTranslationsMs:      loadTranslationsMs,
		RegisterFontsMs:         registerFontsMs,
		RegisterImagesMs:        registerImagesMs,
		ComponentsRenderingMs:   componentsRenderingMs,
		CreateAndSaveOnDiskMs:   createAndSaveOnDiskMs,
		TotalHeapAllocatedBytes: totalHeapAllocatedBytes,
		TotalObjectsAllocated:   totalObjectsAllocated,
	}
}
