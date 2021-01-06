package emergencykit

type pageData struct {
	Css     string
	Content string
}

type contentData struct {
	FirstEncryptedKey  string
	SecondEncryptedKey string
	VerificationCode   string
	CurrentDate        string
	Descriptors        string
	IconHelp           string
	IconPadlock        string
}

const page = `
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Emergency Kit</title>
  
  <style>
    {{.Css}}
  </style>
</head>

<body>
  {{.Content}}
</body>
</html>
  `

const contentEN = `
<header>
<h1>Emergency Kit</h1>
<h2>Verification <span class="verification-code">#{{.VerificationCode}}</span></p>
</header>

<div class="backup">
<div class="intro">
  {{.IconPadlock}}
  <div class="text">
    <h1>Encrypted backup</h1>
    <h2>It can only be decrypted using your <strong>Recovery Code</strong>.</h2>
  </div>
</div>

<div class="keys">

  <div class="key">
    <h3>First key</h3>
    <p>{{.FirstEncryptedKey}}</p>
  </div>

  <div class="key">
    <h3>Second key</h3>
    <p>{{.SecondEncryptedKey}}</p>
  </div>

  <div class="date">
    Created on <date>{{.CurrentDate}}</date>
  </div>
</div>
</div>

<section class="instructions">
<h1>Instructions</h1>
<p>This emergency procedure will help you recover your funds if you are unable to use Muun on your phone.</p>

<div class="item">
  <div class="number-box">
    <div class="number">1</div>
  </div>
  <div class="text-box">
    <h3>Find your Recovery Code</h3>
    <p>You wrote this code on paper before creating your Emergency Kit. You’ll need it later.</p>
  </div>
</div>

<div class="item">
  <div class="number-box">
    <div class="number">2</div>
  </div>
  <div class="text-box">
    <h3>Download the Recovery Tool</h3>
    <p>Go to <a href="https://github.com/muun/recovery">github.com/muun/recovery</a> and download the tool on your computer.</p>
  </div>
</div>

<div class="item">
  <div class="number-box">
    <div class="number">3</div>
  </div>
  <div class="text-box">
    <h3>Recover your funds</h3>
    <p>Run the Recovery Tool and follow the steps. It will safely transfer your funds to a Bitcoin address that you
      choose.</p>
  </div>
</div>
</section>

<section class="help">
{{.IconHelp}}
<div class="text-box">
  <h3>Need help?</h3>
  <p>
    Contact us at <a href="mailto:support@muun.com">support@muun.com</a>. We’re always there to help.
  </p>
</div>
</section>

<section class="advanced page-break-before">
<h1>Advanced information</h1>

<h2>Output descriptors</h2>
<p>These descriptors, combined with your keys, specify how to locate your wallet’s funds on the Bitcoin blockchain.</p>

  {{ if .Descriptors }}
    {{.Descriptors}}
  {{ else }}
    <ul class="descriptors">
      <!-- These lines are way too long, but dividing them introduces unwanted spaces -->
      <li><span class="f">sh</span>(<span class="f">wsh</span>(<span class="f">multi</span>(2, <span class="fp">first key</span>/1'/1'/0/*, <span class="fp">second key</span>/1'/1'/0/*)))</li>
      <li><span class="f">sh</span>(<span class="f">wsh</span>(<span class="f">multi</span>(2, <span class="fp">first key</span>/1'/1'/1/*, <span class="fp">second key</span>/1'/1'/1/*)))</li>
      <li><span class="f">sh</span>(<span class="f">wsh</span>(<span class="f">multi</span>(2, <span class="fp">first key</span>/1'/1'/2/*/*, <span class="fp">second key</span>/1'/1'/2/*/*)))</li>
      <li><span class="f">wsh</span>(<span class="f">multi</span>(2, <span class="fp">first key</span>/1'/1'/0/*, <span class="fp">second key</span>/1'/1'/0/*))</li>
      <li><span class="f">wsh</span>(<span class="f">multi</span>(2, <span class="fp">first key</span>/1'/1'/1/*, <span class="fp">second key</span>/1'/1'/1/*))</li>
      <li><span class="f">wsh</span>(<span class="f">multi</span>(2, <span class="fp">first key</span>/1'/1'/2]/*/*, <span class="fp">second key</span>/1'/1'/2/*/*))</li>
    </ul>
  {{ end }}

<p>
  Output descriptors are part of a developing standard for Recovery that Muun intends to support and is helping grow. 
  Since the standard is in a very early stage, the list above includes some non-standard elements.
</p>

<p>
  When descriptors reach a more mature stage, you’ll be able to take your funds from one wallet to another with 
  complete independence. Muun believes this freedom is at the core of Bitcoin’s promise, and is working towards 
  that goal.
</p>
</section>
`

const contentES = `
<header>
<h1>Kit de Emergencia</h1>
<h2>Verificación <span class="verification-code">#{{.VerificationCode}}</span></p>
</header>

<div class="backup">
<div class="intro">
  {{.IconPadlock}}
  <div class="text">
    <h1>Respaldo encriptado</h1>
    <h2>Sólo puede ser desencriptado con tu <strong>Código de Recuperación</strong>.</h2>
  </div>
</div>

<div class="keys">

  <div class="key">
    <h3>Primera clave</h3>
    <p>{{.FirstEncryptedKey}}</p>
  </div>

  <div class="key">
    <h3>Segunda clave</h3>
    <p>{{.SecondEncryptedKey}}</p>
  </div>

  <div class="date">
    Creado el <date>{{.CurrentDate}}</date>
  </div>
</div>
</div>

<section class="instructions">
<h1>Instrucciones</h1>
<p>Éste procedimiento de emergencia te ayudará a recuperar tus fondos si no puedes usar Muun en tu teléfono.</p>

<div class="item">
  <div class="number-box">
    <div class="number">1</div>
  </div>
  <div class="text-box">
    <h3>Encuentra tu Código de Recuperación</h3>
    <p>Lo escribiste en papel antes de crear tu Kit de Emergencia. Lo necesitarás después.</p>
  </div>
</div>

<div class="item">
  <div class="number-box">
    <div class="number">2</div>
  </div>
  <div class="text-box">
    <h3>Descarga la Herramienta de Recuperación</h3>
    <p>Ingresa en <a href="github.com/muun/recovery">github.com/muun/recovery</a> y descarga la herramienta en tu computadora..</p>
  </div>
</div>

<div class="item">
  <div class="number-box">
    <div class="number">3</div>
  </div>
  <div class="text-box">
    <h3>Recupera tus fondos</h3>
    <p>Ejecuta la Herramienta de Recuperación y sigue los pasos. Transferirá tus fondos a una dirección de Bitcoin que elijas.</p>
  </div>
</div>
</section>

<section class="help">
{{.IconHelp}}
<div class="text-box">
  <h3>¿Necesitas ayuda?</h3>
  <p>
  Contáctanos en <a href="mailto:support@muun.com">support@muun.com</a>. Siempre estamos disponibles para ayudar.
  </p>
</div>
</section>

<section class="advanced page-break-before">
<h1>Información Avanzada</h1>

<h2>Output descriptors</h2>
<p>Estos descriptors, combinados con tus claves, indican cómo encontrar los fondos de tu billetera en la blockchain de Bitcoin.</p>

  {{ if .Descriptors }}
    {{.Descriptors}}
  {{ else }}
    <ul class="descriptors">
      <!-- These lines are way too long, but dividing them introduces unwanted spaces -->
      <li><span class="f">sh</span>(<span class="f">wsh</span>(<span class="f">multi</span>(2, <span class="fp">primera clave</span>/1'/1'/0/*, <span class="fp">segunda clave</span>/1'/1'/0/*)))</li>
      <li><span class="f">sh</span>(<span class="f">wsh</span>(<span class="f">multi</span>(2, <span class="fp">primera clave</span>/1'/1'/1/*, <span class="fp">segunda clave</span>/1'/1'/1/*)))</li>
      <li><span class="f">sh</span>(<span class="f">wsh</span>(<span class="f">multi</span>(2, <span class="fp">primera clave</span>/1'/1'/2/*/*, <span class="fp">segunda clave</span>/1'/1'/2/*/*)))</li>
      <li><span class="f">wsh</span>(<span class="f">multi</span>(2, <span class="fp">primera clave</span>/1'/1'/0/*, <span class="fp">segunda clave</span>/1'/1'/0/*))</li>
      <li><span class="f">wsh</span>(<span class="f">multi</span>(2, <span class="fp">primera clave</span>/1'/1'/1/*, <span class="fp">segunda clave</span>/1'/1'/1/*))</li>
      <li><span class="f">wsh</span>(<span class="f">multi</span>(2, <span class="fp">primera clave</span>/1'/1'/2]/*/*, <span class="fp">segunda clave</span>/1'/1'/2/*/*))</li>
    </ul>
  {{ end }}

<p>
Los output descriptors son parte de un estándar de recuperación actualmente en desarrollo. Muun tiene la intención 
de soportar este estándar y apoyar su crecimiento. Dado que se encuentra en una etapa muy temprana, la siguiente lista 
incluye algunos elementos que aún no están estandarizados.
</p>

<p>
Cuando los descriptors lleguen a una etapa más madura, podrás llevar tus fondos de una billetera a la otra con completa 
independencia. Muun cree que ésta libertad es central a la promesa de Bitcoin, y está trabajando para que eso suceda.
</p>
</section>
`

const iconHelp = `
<svg width="72" height="72" viewBox="0 0 72 72" fill="none" xmlns="http://www.w3.org/2000/svg">
<g clip-path="url(#clip0)">
<g filter="url(#filter0_d)">
<circle cx="36" cy="36" r="28" fill="white"/>
</g>
<path d="M51.9762 41.7833L51.9999 28.1164C52.005 27.3149 51.8507 26.5203 51.5461 25.7789C51.2414 25.0374 50.7924 24.3638 50.2252 23.7972C49.6572 23.2232 48.9802 22.7686 48.2338 22.4599C47.4874 22.1513 46.6869 21.995 45.8791 22.0001H26.1208C24.4981 22.0022 22.9424 22.6473 21.795 23.7938C20.6476 24.9404 20.0021 26.4949 20 28.1164V41.2789C20.0021 42.9004 20.6476 44.4548 21.795 45.6014C22.9424 46.748 24.4981 47.393 26.1208 47.3951H26.5625V51.1547C26.5578 51.7232 26.7257 52.2798 27.0439 52.7512C27.3621 53.2225 27.8158 53.5865 28.3451 53.7951C28.6816 53.9281 29.04 53.9976 29.402 54C29.7741 53.9998 30.1425 53.9251 30.4852 53.7803C30.828 53.6354 31.1382 53.4234 31.3975 53.1567L36.6901 47.3951L48.1895 46.2128L51.9762 41.7833ZM35.8698 45.3774L29.7175 51.4778C29.6594 51.5449 29.5815 51.5917 29.495 51.6116C29.4085 51.6314 29.3179 51.6232 29.2363 51.5882C29.1493 51.5551 29.075 51.4953 29.024 51.4175C28.973 51.3396 28.948 51.2476 28.9524 51.1547V46.2128C28.9524 45.8993 28.8277 45.5986 28.6059 45.3769C28.384 45.1551 28.083 45.0306 27.7693 45.0306H26.1208C25.125 45.0306 24.17 44.6353 23.4659 43.9317C22.7618 43.2281 22.3663 42.2739 22.3663 41.2789V28.1164C22.3663 27.1213 22.7618 26.1671 23.4659 25.4635C24.17 24.7599 25.125 24.3646 26.1208 24.3646H45.8791C46.372 24.3641 46.86 24.4614 47.315 24.6508C47.7699 24.8402 48.1827 25.118 48.5293 25.4681C48.8797 25.8145 49.1577 26.227 49.3472 26.6816C49.5368 27.1362 49.6341 27.6239 49.6336 28.1164V41.2789C49.6336 42.2739 49.238 43.2281 48.5339 43.9317C47.8298 44.6353 46.8749 45.0306 45.8791 45.0306H36.6901C36.3764 45.0309 36.0757 45.1556 35.854 45.3774H35.8698ZM36.6901 47.3951H45.8791C47.4141 47.3926 48.8922 46.8146 50.0211 45.7755C51.1501 44.7364 51.8478 43.3117 51.9762 41.7833L48.1895 46.2128L36.6901 47.3951Z" fill="#2474CD"/>
<path d="M34.708 37.1242C34.612 37.1242 34.528 37.0942 34.456 37.034C34.384 36.9619 34.348 36.8777 34.348 36.7815V36.3666C34.432 35.8615 34.618 35.4105 34.906 35.0136C35.206 34.6168 35.614 34.1658 36.13 33.6607C36.514 33.2758 36.802 32.9631 36.994 32.7226C37.186 32.4701 37.288 32.2175 37.3 31.965C37.336 31.5922 37.21 31.2975 36.922 31.081C36.646 30.8525 36.31 30.7383 35.914 30.7383C34.978 30.7383 34.402 31.1893 34.186 32.0912C34.09 32.3799 33.904 32.5242 33.628 32.5242H31.432C31.3 32.5242 31.192 32.4821 31.108 32.3979C31.036 32.3017 31 32.1814 31 32.0371C31.024 31.3757 31.234 30.7503 31.63 30.161C32.026 29.5597 32.608 29.0727 33.376 28.6998C34.144 28.327 35.062 28.1406 36.13 28.1406C37.222 28.1406 38.11 28.315 38.794 28.6638C39.478 29.0005 39.964 29.4214 40.252 29.9265C40.552 30.4196 40.702 30.9247 40.702 31.4418C40.702 32.0311 40.564 32.5482 40.288 32.9932C40.024 33.4382 39.628 33.9493 39.1 34.5266C38.776 34.8753 38.518 35.17 38.326 35.4105C38.146 35.651 38.008 35.9036 37.912 36.1681C37.876 36.2764 37.834 36.4387 37.786 36.6552C37.69 36.8236 37.606 36.9438 37.534 37.016C37.462 37.0881 37.36 37.1242 37.228 37.1242H34.708ZM34.744 40.9486C34.612 40.9486 34.504 40.9065 34.42 40.8223C34.336 40.7381 34.294 40.6299 34.294 40.4976V38.4411C34.294 38.3088 34.336 38.2006 34.42 38.1164C34.504 38.0322 34.612 37.9901 34.744 37.9901H37.048C37.18 37.9901 37.288 38.0322 37.372 38.1164C37.468 38.2006 37.516 38.3088 37.516 38.4411V40.4976C37.516 40.6299 37.468 40.7381 37.372 40.8223C37.288 40.9065 37.18 40.9486 37.048 40.9486H34.744Z" fill="#182449"/>
</g>
<defs>
<filter id="filter0_d" x="0" y="4" width="72" height="72" filterUnits="userSpaceOnUse" color-interpolation-filters="sRGB">
<feFlood flood-opacity="0" result="BackgroundImageFix"/>
<feColorMatrix in="SourceAlpha" type="matrix" values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 127 0"/>
<feOffset dy="4"/>
<feGaussianBlur stdDeviation="4"/>
<feColorMatrix type="matrix" values="0 0 0 0 0.124943 0 0 0 0 0.228158 0 0 0 0 0.346117 0 0 0 0.05 0"/>
<feBlend mode="normal" in2="BackgroundImageFix" result="effect1_dropShadow"/>
<feBlend mode="normal" in="SourceGraphic" in2="effect1_dropShadow" result="shape"/>
</filter>
<clipPath id="clip0">
<rect width="72" height="72" fill="white"/>
</clipPath>
</defs>
</svg>
`

const iconPadlock = `
<svg width="72" height="72" viewBox="0 0 72 72" fill="none" xmlns="http://www.w3.org/2000/svg">
<g clip-path="url(#clip0)">
<g filter="url(#filter0_dd)">
<g filter="url(#filter1_i)">
<path d="M48.7367 30.2734H23.2633C21.461 30.2734 20 31.7345 20 33.5367V53.192C20 54.9942 21.461 56.4553 23.2633 56.4553H48.7367C50.539 56.4553 52 54.9942 52 53.192V33.5367C52 31.7345 50.539 30.2734 48.7367 30.2734Z" fill="url(#paint0_linear)"/>
</g>
<path d="M38.9119 41.1786C38.9119 42.7853 37.6095 44.0877 36.0028 44.0877C34.3962 44.0877 33.0938 42.7853 33.0938 41.1786C33.0938 39.572 34.3962 38.2695 36.0028 38.2695C37.6095 38.2695 38.9119 39.572 38.9119 41.1786Z" fill="url(#paint1_radial)"/>
<path d="M34.4106 43.9113C34.4915 43.5876 34.7824 43.3604 35.1161 43.3604H36.8895C37.2233 43.3604 37.5142 43.5876 37.5951 43.9113L38.686 48.275C38.8008 48.734 38.4536 49.1786 37.9805 49.1786H34.0252C33.5521 49.1786 33.2049 48.734 33.3197 48.275L34.4106 43.9113Z" fill="url(#paint2_radial)"/>
<g filter="url(#filter2_i)">
<path d="M25.0906 24.8182V30.2727H29.0906V24.8182C29.0906 22.8788 30.4724 19 35.9997 19C41.5269 19 42.9088 22.8788 42.9088 24.8182V30.2727H46.9088V24.8182C46.9088 21.5455 44.7269 15 35.9997 15C27.2724 15 25.0906 21.5455 25.0906 24.8182Z" fill="#2573F7"/>
<path d="M25.0906 24.8182V30.2727H29.0906V24.8182C29.0906 22.8788 30.4724 19 35.9997 19C41.5269 19 42.9088 22.8788 42.9088 24.8182V30.2727H46.9088V24.8182C46.9088 21.5455 44.7269 15 35.9997 15C27.2724 15 25.0906 21.5455 25.0906 24.8182Z" fill="url(#paint3_linear)"/>
</g>
</g>
</g>
<defs>
<filter id="filter0_dd" x="7.99957" y="8.99978" width="56.0009" height="65.4561" filterUnits="userSpaceOnUse" color-interpolation-filters="sRGB">
<feFlood flood-opacity="0" result="BackgroundImageFix"/>
<feColorMatrix in="SourceAlpha" type="matrix" values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 127 0"/>
<feOffset dy="6.00022"/>
<feGaussianBlur stdDeviation="6.00022"/>
<feColorMatrix type="matrix" values="0 0 0 0 0.340702 0 0 0 0 0.386926 0 0 0 0 0.529451 0 0 0 0.3 0"/>
<feBlend mode="normal" in2="BackgroundImageFix" result="effect1_dropShadow"/>
<feColorMatrix in="SourceAlpha" type="matrix" values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 127 0"/>
<feOffset dy="1.50005"/>
<feGaussianBlur stdDeviation="1.50005"/>
<feColorMatrix type="matrix" values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.12 0"/>
<feBlend mode="normal" in2="effect1_dropShadow" result="effect2_dropShadow"/>
<feBlend mode="normal" in="SourceGraphic" in2="effect2_dropShadow" result="shape"/>
</filter>
<filter id="filter1_i" x="19.5921" y="29.4576" width="32.4079" height="26.9976" filterUnits="userSpaceOnUse" color-interpolation-filters="sRGB">
<feFlood flood-opacity="0" result="BackgroundImageFix"/>
<feBlend mode="normal" in="SourceGraphic" in2="BackgroundImageFix" result="shape"/>
<feColorMatrix in="SourceAlpha" type="matrix" values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 127 0" result="hardAlpha"/>
<feOffset dx="-0.40791" dy="-0.815819"/>
<feGaussianBlur stdDeviation="0.815819"/>
<feComposite in2="hardAlpha" operator="arithmetic" k2="-1" k3="1"/>
<feColorMatrix type="matrix" values="0 0 0 0 0.25098 0 0 0 0 0.380392 0 0 0 0 0.552941 0 0 0 1 0"/>
<feBlend mode="normal" in2="shape" result="effect1_innerShadow"/>
</filter>
<filter id="filter2_i" x="24.7156" y="14.625" width="22.1932" height="15.6477" filterUnits="userSpaceOnUse" color-interpolation-filters="sRGB">
<feFlood flood-opacity="0" result="BackgroundImageFix"/>
<feBlend mode="normal" in="SourceGraphic" in2="BackgroundImageFix" result="shape"/>
<feColorMatrix in="SourceAlpha" type="matrix" values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 127 0" result="hardAlpha"/>
<feOffset dx="-0.375014" dy="-0.375014"/>
<feGaussianBlur stdDeviation="0.375014"/>
<feComposite in2="hardAlpha" operator="arithmetic" k2="-1" k3="1"/>
<feColorMatrix type="matrix" values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.12 0"/>
<feBlend mode="normal" in2="shape" result="effect1_innerShadow"/>
</filter>
<linearGradient id="paint0_linear" x1="25.8754" y1="40.3205" x2="64.6733" y2="80.0278" gradientUnits="userSpaceOnUse">
<stop stop-color="#91ACC9"/>
<stop offset="0.561326" stop-color="#3D5F8C"/>
</linearGradient>
<radialGradient id="paint1_radial" cx="0" cy="0" r="1" gradientUnits="userSpaceOnUse" gradientTransform="translate(36.0028 43.7241) rotate(90) scale(5.45455 2.90909)">
<stop stop-color="#0B141D"/>
<stop offset="1" stop-color="#27394D"/>
</radialGradient>
<radialGradient id="paint2_radial" cx="0" cy="0" r="1" gradientUnits="userSpaceOnUse" gradientTransform="translate(36.0028 43.7241) rotate(90) scale(5.45455 2.90909)">
<stop stop-color="#0B141D"/>
<stop offset="1" stop-color="#27394D"/>
</radialGradient>
<linearGradient id="paint3_linear" x1="35.9997" y1="26.0114" x2="35.9997" y2="34.4318" gradientUnits="userSpaceOnUse">
<stop stop-color="#435F7D"/>
<stop offset="1" stop-color="#213953"/>
</linearGradient>
<clipPath id="clip0">
<rect width="72" height="72" fill="white"/>
</clipPath>
</defs>
</svg>
`
