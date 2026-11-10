package model

type RenderProfiling struct {
	LoadTranslationsMs      int64
	RegisterFontsMs         int64
	RegisterImagesMs        int64
	ComponentsRenderingMs   int64
	CreateAndSaveOnDiskMs   int64
	TotalHeapAllocatedBytes int64
	TotalObjectsAllocated   int64
	EmbedMetadataMs         int64
	KitSizeBytes            int64
	DrawIconsMs             int64
}
