package provider

type Theme struct {
	PrimaryColorHex uint32
	SurfaceColorHex uint32
}

func NewTheme(
	primaryColorHex uint32,
	surfaceColorHex uint32,
) Theme {
	return Theme{
		PrimaryColorHex: primaryColorHex,
		SurfaceColorHex: surfaceColorHex,
	}
}
