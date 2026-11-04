package provider

type Provider struct {
	UUID       string
	Name       string
	SiteURL    string
	LightTheme Theme
	DarkTheme  Theme
}

func NewProvider(
	uuid string,
	name string,
	siteURL string,
	lightTheme Theme,
	darkTheme Theme,
) Provider {
	return Provider{
		UUID:       uuid,
		Name:       name,
		SiteURL:    siteURL,
		LightTheme: lightTheme,
		DarkTheme:  darkTheme,
	}
}
