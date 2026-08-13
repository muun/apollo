package go_render

import (
	"runtime"
	"time"

	"github.com/go-errors/errors"

	"github.com/muun/libwallet/data/emergency_kit"
	"github.com/muun/libwallet/data/emergency_kit/resources"
	"github.com/muun/libwallet/domain/model/emergency_kit/go_render/assets"
	"github.com/muun/libwallet/domain/model/emergency_kit/go_render/components"
	"github.com/muun/libwallet/domain/model/emergency_kit/go_render/components/advanced"
	"github.com/muun/libwallet/domain/model/emergency_kit/go_render/components/keys"
	"github.com/muun/libwallet/emergencykit"
)

// GeneratedEKPDF is a model including the path in which Libwallet left the generated pdf, the
// verificationCode and the version
type GeneratedEKPDF struct {
	Path             string
	VerificationCode string
	Version          int
	Profiling        *RenderProfiling // per-stage timings and allocation stats for this render
}

// RenderProfiling captures per-stage durations and allocation totals for a single Render call.
// Alloc stats are a process-wide delta from runtime.MemStats, so they are an approximation: other
// goroutines allocating concurrently during the render pollute the number.
type RenderProfiling struct {
	LoadTranslationsMs      int64
	RegisterFontsMs         int64
	RegisterImagesMs        int64
	ComponentsRenderingMs   int64
	CreateAndSaveOnDiskMs   int64
	TotalHeapAllocatedBytes int64
	TotalObjectsAllocated   int64
	EmbedMetadataMs         int64
}

func NewRenderProfiling(
	loadTranslationsMs int64,
	registerFontsMs int64,
	registerImagesMs int64,
	componentsRenderingMs int64,
	createAndSaveOnDiskMs int64,
	totalHeapAllocatedBytes int64,
	totalObjectsAllocated int64,
) *RenderProfiling {
	return &RenderProfiling{
		LoadTranslationsMs:      loadTranslationsMs,
		RegisterFontsMs:         registerFontsMs,
		RegisterImagesMs:        registerImagesMs,
		ComponentsRenderingMs:   componentsRenderingMs,
		CreateAndSaveOnDiskMs:   createAndSaveOnDiskMs,
		TotalHeapAllocatedBytes: totalHeapAllocatedBytes,
		TotalObjectsAllocated:   totalObjectsAllocated,
	}
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
	return result, nil
}

func loadTranslations(lang string) (*assets.Translations, error) {
	language := assets.English
	if lang == "es" {
		language = assets.Spanish
	}

	return assets.LoadTranslations(language)
}
