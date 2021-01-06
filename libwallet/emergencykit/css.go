package emergencykit

// NOTE:
// To view this file comfortably, disable line-wrapping in your editor.

const css = `
@page {
  size:  auto;
  margin: 0mm;  /* remove margin in printer settings */
}

* {
  box-sizing: border-box;

  margin: 0;
  padding: 0;
  margin-block-start: 0;
  margin-block-end: 0;

  font-family: -apple-system, Roboto;
}

body {
  width: 480px;
}

strong {
  font-weight: 500;
}

header {
  display: flex;
  justify-content: space-between;
  padding: 20px 16px;
  background-color: #F7FBFF;
}

h1 {
  margin: 0;
  font-family: -apple-system, Roboto;
  font-weight: 500;
  font-size: 24px;
  line-height: 30px;
  color: #182449;
}

h2 {
  color: #182449;
}

h3 {
  color: #182449;
}

header h2 {
  margin: 3px 0 0 0;
  font-family: Menlo, Roboto Mono;
  font-weight: 500;
  font-size: 18px;
  line-height: 27px;
  color: #576580;
  text-transform: uppercase;
}

a {
  color: #337cd0;
}

.backup {
  margin: 24px 16px 0 16px;
  background-color: #DFECFB;
}

.backup .intro {
  display: flex;
  padding: 16px 16px 16px 0;
}

.backup h1 {
  margin-top: 16px;
  margin-bottom: 8px;
  font-weight: 500;
  font-size: 32px;
  line-height: 32px;
  line-height: 32px;
}

.backup h2 {
  font-size: 15px;
  font-weight: 400;
  font-size: 15px;
  line-height: 24px;
  color: #57656F;
}

.backup h2 strong {
  color: #182449;
}

.backup h3 {
  margin-bottom: 8px;
  font-weight: 500;
  font-size: 18px;
  line-height: 24px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.backup svg {
  margin: 8px 4px 0;
}

.backup .key {
  padding: 32px 16px 24px 16px;
  margin: 0 4px 4px 4px;
  background-color: white;
}

.backup .key p {
  font-family: Menlo, Roboto Mono;
  font-weight: 400;
  font-size: 13px;
  word-wrap: break-word;
  line-height: 24px;
  color: #57656F;
}

.backup .date {
  padding: 12px 0;
  font-size: 13px;
  line-height: 24px;
  text-align: center;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: #57656F;
}

.backup .date date {
  font-weight: 500;
  color: #182449;
}

section {
  margin-top: 40px;
  padding: 16px;
}

section h2 {
  margin-top: 24px;
  font-weight: 500;
  font-size: 20px;
  line-height: 32px;
}

section h3 {
  font-weight: 500;
  font-size: 20px;
  line-height: 32px;
  color: #182449;
}

section p {
  margin-top: 8px;
  font-weight: normal;
  font-size: 16px;
  line-height: 24px;
  color: #576580;
}

.instructions .item {
  display: flex;
  margin-top: 32px;
}

.instructions .item .number-box {
  margin-right: 16px;
  padding-top: 6px;
}

.instructions .item .number {
  width: 20px;
  border-radius: 50%;
  text-align: center;
  font-weight: 500;
  font-size: 14px;
  line-height: 20px;
  background: #2474CD;
  color: #ffffff;
}

.help {
  display: flex;
  padding: 32px 16px 32px 0;
  background: #F6F9FF;
}

.help svg {
  margin: 0 8px;
}

.descriptors {
  margin: 16px 0;
  padding: 16px;
  font-family: Menlo, Roboto Mono;
  font-weight: 500;
  font-size: 9px;
  line-height: 240%;
  letter-spacing: 1px;
  list-style-type: none;
  background: #F6F9FF;
  color: #576580;
}

.descriptors .f {
  color: #447BEF;
}

.descriptors .fp {
  color: #d74a41;
}

.descriptors .checksum {
  color: #a42fa2;
}

@media print {
  .page-break-before { page-break-before: always; }
}
`
