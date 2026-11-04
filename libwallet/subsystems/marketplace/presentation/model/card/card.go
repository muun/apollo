package card

type Card struct {
	UUID          string
	SKU           string
	ImageURL      string
	HasStock      bool
	Material      Material
	WidthMm       float32
	HeightMm      float32
	ThicknessMm   float32
	WeightGrams   float32
	SecureElement SecureElement
}

func NewCard(
	uuid string,
	sku string,
	imageURL string,
	hasStock bool,
	material Material,
	widthMm float32,
	heightMm float32,
	thicknessMm float32,
	weightGrams float32,
	secureElement SecureElement,
) Card {
	return Card{
		UUID:          uuid,
		SKU:           sku,
		ImageURL:      imageURL,
		HasStock:      hasStock,
		Material:      material,
		WidthMm:       widthMm,
		HeightMm:      heightMm,
		ThicknessMm:   thicknessMm,
		WeightGrams:   weightGrams,
		SecureElement: secureElement,
	}
}
