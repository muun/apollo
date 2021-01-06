package emergencykit

import (
	"fmt"
	"strings"
)

type DescriptorsData struct {
	FirstFingerprint  string
	SecondFingerprint string
}

// Output descriptors shown in the PDF do not include legacy descriptors no longer in use. We leave
// the decision of whether to scan them to the Recovery Tool.
var descriptorFormats = []string{
	"sh(wsh(multi(2, %s/1'/1'/0/*, %s/1'/1'/0/*)))", // V3 change
	"sh(wsh(multi(2, %s/1'/1'/1/*, %s/1'/1'/1/*)))", // V3 external
	"wsh(multi(2, %s/1'/1'/0/*, %s/1'/1'/0/*))",     // V4 change
	"wsh(multi(2, %s/1'/1'/1/*, %s/1'/1'/1/*))",     // V4 external
}

// GetDescriptors returns an array of raw output descriptors.
func GetDescriptors(data *DescriptorsData) []string {
	var descriptors []string

	for _, descriptorFormat := range descriptorFormats {
		descriptor := fmt.Sprintf(descriptorFormat, data.FirstFingerprint, data.SecondFingerprint)
		checksum := calculateChecksum(descriptor)

		descriptors = append(descriptors, descriptor+"#"+checksum)
	}

	return descriptors
}

// GetDescriptorsHTML returns the HTML for the output descriptor list in the Emergency Kit.
func GetDescriptorsHTML(data *DescriptorsData) string {
	descriptors := GetDescriptors(data)

	var itemsHTML []string

	for _, descriptor := range descriptors {
		descriptor, checksum := splitChecksum(descriptor)

		html := descriptor

		// Replace script type expressions (parenthesis in match prevent replacing the "sh" in "wsh")
		html = strings.ReplaceAll(html, "wsh(", renderScriptType("wsh")+"(")
		html = strings.ReplaceAll(html, "sh(", renderScriptType("sh")+"(")
		html = strings.ReplaceAll(html, "multi(", renderScriptType("multi")+"(")

		// Replace fingerprint expressions:
		html = strings.ReplaceAll(html, data.FirstFingerprint, renderFingerprint(data.FirstFingerprint))
		html = strings.ReplaceAll(html, data.SecondFingerprint, renderFingerprint(data.SecondFingerprint))

		// Add checksum and wrap everything:
		html += renderChecksum(checksum)
		html = renderItem(html)

		itemsHTML = append(itemsHTML, html)
	}

	return renderList(itemsHTML)
}

func renderList(itemsHTML []string) string {
	return fmt.Sprintf(`<ul class="descriptors">%s</ul>`, strings.Join(itemsHTML, "\n"))
}

func renderItem(innerHTML string) string {
	return fmt.Sprintf(`<li>%s</li>`, innerHTML)
}

func renderScriptType(scriptType string) string {
	return fmt.Sprintf(`<span class="f">%s</span>`, scriptType)
}

func renderFingerprint(fingerprint string) string {
	return fmt.Sprintf(`<span class="fp">%s</span>`, fingerprint)
}

func renderChecksum(checksum string) string {
	return fmt.Sprintf(`#<span class="checksum">%s</span>`, checksum)
}

func splitChecksum(descriptor string) (string, string) {
	parts := strings.Split(descriptor, "#")

	if len(parts) == 1 {
		return parts[0], ""
	}

	return parts[0], parts[1]
}

// -------------------------------------------------------------------------------------------------
// WARNING:
// Below this point, you may find only fear and confusion.

// I translated the code for computing checksums from the original C++ in the bitcoind source,
// making a few adjustments for language differences. It's a specialized algorithm for the domain of
// output descriptors, and it uses the same primitives as the bech32 encoding.

var inputCharset = "0123456789()[],'/*abcdefgh@:$%{}IJKLMNOPQRSTUVWXYZ&+-.;<=>?!^_|~ijklmnopqrstuvwxyzABCDEFGH`#\"\\ "
var checksumCharset = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

func calculateChecksum(desc string) string {
	var c uint64 = 1
	var cls int = 0
	var clscount int = 0

	for _, ch := range desc {
		pos := strings.IndexRune(inputCharset, ch)

		if pos == -1 {
			return ""
		}

		c = polyMod(c, pos&31)
		cls = cls*3 + (pos >> 5)

		clscount++
		if clscount == 3 {
			c = polyMod(c, cls)
			cls = 0
			clscount = 0
		}
	}

	if clscount > 0 {
		c = polyMod(c, cls)
	}

	for i := 0; i < 8; i++ {
		c = polyMod(c, 0)
	}

	c ^= 1

	ret := make([]byte, 8)
	for i := 0; i < 8; i++ {
		ret[i] = checksumCharset[(c>>(5*(7-i)))&31]
	}

	return string(ret)
}

func polyMod(c uint64, intVal int) uint64 {
	val := uint64(intVal)

	c0 := c >> 35
	c = ((c & 0x7ffffffff) << 5) ^ val

	if c0&1 != 0 {
		c ^= 0xf5dee51989
	}
	if c0&2 != 0 {
		c ^= 0xa9fdca3312
	}
	if c0&4 != 0 {
		c ^= 0x1bab10e32d
	}
	if c0&8 != 0 {
		c ^= 0x3706b1677a
	}
	if c0&16 != 0 {
		c ^= 0x644d626ffd
	}

	return c
}
