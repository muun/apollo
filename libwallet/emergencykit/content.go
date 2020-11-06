package emergencykit

type pageData struct {
	Css     string
	Logo    string
	Content string
}

type contentData struct {
	FirstEncryptedKey  string
	SecondEncryptedKey string
	VerificationCode   string
	CurrentDate        string
}

const logo = `
<svg class="logo" width="65" height="12" viewBox="0 0 65 12" fill="none" xmlns="http://www.w3.org/2000/svg">
  <path d="M2.89661 11.8674V3.24807C2.89661 3.24807 3.72365 2.86307 5.30403 2.86307C6.88441 2.86307 7.58862 3.41307 7.58862 4.96093V11.8674H9.85684V4.96093C9.85684 4.26164 9.85684 3.72736 9.84865 3.31878C10.2089 3.13021 10.9132 2.86307 12.0759 2.86307C13.8774 2.86307 14.5489 3.41307 14.5489 4.96093V11.8674H16.9972V4.96093C16.9972 3.91593 16.7679 2.82378 15.9491 2.03021C15.1221 1.23664 13.9429 0.710205 12.1005 0.710205C10.4464 0.710205 8.98886 1.25235 8.70226 1.37021C7.77696 0.953777 6.47499 0.710205 5.32041 0.710205C3.07675 0.710205 0.456431 1.39378 0.456431 1.39378V11.8674H2.89661Z" fill="#2474CD"/>
  <path d="M32.8274 0.710205V11.1838C32.8274 11.1838 30.2309 11.8674 27.7067 11.8674C25.8819 11.8674 24.4268 11.3331 23.6149 10.5474C22.803 9.7538 22.5859 8.66166 22.5859 7.61665V0.710205H24.9895V7.6088C24.9895 9.15666 25.9542 9.70666 27.7227 9.70666C29.4913 9.70666 30.4318 9.32166 30.4318 9.32166V0.710205H32.8274Z" fill="#2474CD"/>
  <path d="M48.9016 0.710205V11.1838C48.9016 11.1838 46.3051 11.8674 43.7809 11.8674C41.9561 11.8674 40.501 11.3331 39.6891 10.5474C38.8772 9.7538 38.6602 8.66166 38.6602 7.61665V0.710205H41.0638V7.6088C41.0638 9.15666 42.0284 9.70666 43.797 9.70666C45.5655 9.70666 46.506 9.32166 46.506 9.32166V0.710205H48.9016Z" fill="#2474CD"/>
  <path d="M54.7012 11.8674V1.39378C54.7012 1.39378 57.2977 0.710205 59.8219 0.710205C61.6467 0.710205 63.1017 1.24449 63.9137 2.03021C64.7256 2.82378 64.9426 3.91593 64.9426 4.96093V11.8674H62.539V4.96879C62.539 3.42093 61.5744 2.87093 59.8058 2.87093C58.0373 2.87093 57.0967 3.25593 57.0967 3.25593V11.8674H54.7012Z" fill="#2474CD"/>
</svg>
  `

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
  {{.Logo}}
  {{.Content}}
</body>
</html>
  `

const contentEN = `
<header>
  <div class="title">
    <h1>Emergency Kit</h1>
    <date>Created on {{.CurrentDate}}</date>
  </div>

  <div class="verification-code">
    <h4>Verification code</h4>
    <code>{{.VerificationCode}}</code>
  </div>
</header>

<section>
  <h2>About this document</h2>

  <p>Here you'll find the encrypted information you need to transfer your money out of your Muun wallet without
    requiring collaboration from anyone, including Muun's own software and servers.</p>

  <p>This includes all your private keys (securely encrypted with your Recovery Code) and some additional data related 
    to your wallet.</p>

  <p>With this document and your recovery code at hand, you have complete ownership of your money. Nobody else has 
    all the pieces. This is why Bitcoin was created: to give people full control of the money they rightly own.</p>
</section>

<section>
  <h2>Recovering your money</h2>

  <p>To move forward with the transfer of funds, we recommend using our 
    <a href="https://github.com/muun/recovery">open-source Recovery Tool</a>. It's available for the whole world to 
    download and examine, and it will always be.</p>

  <p>We created it to assist you with the process, but nothing stops you from doing it manually if you're so 
    inclined.</p>

  <p>Go to <strong>github.com/muun/recovery</strong> and follow the instructions to easily transfer your money to a Bitcoin 
    address of your choosing.</p>
</section>

<section>
  <h2>Recovery information</h2>

  <p>This is what you'll need for the transfer, plus your recovery code. If these random-seeming codes look daunting, 
    don't worry: the <a href="https://github.com/muun/recovery">recovery tool</a> will take care of everything.</p>
</section>

<section>
  <h3>First Encrypted Private Key</h3>
  <div class="data">{{.FirstEncryptedKey}}</div>

  <h3>Second Encrypted Private Key</h3>
  <div class="data">{{.SecondEncryptedKey}}</div>
  
  <h3>Output descriptors</h3>
  <div class="data">
    sh(wsh(multi(2, <span class="key-placeholder">first key</span>/1'/1'/0/*, <span class="key-placeholder">second key</span>/1'/1'/0/*)))<br>
    sh(wsh(multi(2, <span class="key-placeholder">first key</span>/1'/1'/1/*, <span class="key-placeholder">second key</span>/1'/1'/1/*)))<br>
    sh(wsh(multi(2, <span class="key-placeholder">first key</span>/1'/1'/2/*/*, <span class="key-placeholder">second key</span>/1'/1'/2/*/*)))<br>
    wsh(multi(2, <span class="key-placeholder">first key</span>/1'/1'/0/*, <span class="key-placeholder">second key</span>/1'/1'/0/*))<br>
    wsh(multi(2, <span class="key-placeholder">first key</span>/1'/1'/1/*, <span class="key-placeholder">second key</span>/1'/1'/1/*))<br>
    wsh(multi(2, <span class="key-placeholder">first key</span>/1'/1'/2/*/*, <span class="key-placeholder">second key</span>/1'/1'/2/*/*))
  </div>
</section>

<section class="page-break-before">
  <h2>Some questions you might have</h2>

  <h3>Can I print this document?</h3>

  <p>You can, but we recommend storing it online in a service such as Google Drive, iCloud, OneDrive or Dropbox. 
    These providers have earned their users' trust by being always available and safeguarding data with strong 
    security practices. They are also free.</p>

  <p>If you decide to print it, be sure to keep it safely away from where you store your recovery code. Remember: 
    a person with both pieces can take control of your funds.</p>

  <h3>What if I lose my emergency kit?</h3>

  <p>Don't panic. Your money is not lost. It's all there, in the Bitcoin blockchain, waiting for you. Use our Android 
  or iOS applications and go to the Security Center to create a new kit.</p>
  
  <h3>What if somebody sees this document?</h3>
  <p>
  As long as you keep your recovery code hidden, this document is harmless. All the data it contains is safely 
  encrypted, and only your recovery code can decrypt it to a usable form.</p>
  
  <p>Still, we recommend that you keep it where only you can see it. If you really fear losing it or want to share it
    for some other reason, only do so with people that enjoy your absolute trust.</p>
  
  <h3>Why don't I have a mnemonic phrase?</h3>
  
  <p>If you've been involved with Bitcoin for some time, you've probably seen mnemonics and been told to rely on them.
    As of this writing, many wallets still use the technique.</p>
  
  <p>There's nothing inherently wrong with mnemonics, but they have been rendered obsolete. The twelve words are 
    simply not enough to encode all the information a modern Bitcoin wallet requires to operate, and the problem will 
    only get worse as technology advances. Already there are improvements taking shape that would make mnemonic 
    recovery not only harder, but impossible.</p>
  
  <p>For this reason, we decided to guarantee full ownership using a safer, more flexible and future-proof technique. 
    This way, we'll be able to keep up with technological improvements and continue to provide 
    state-of-the-art software.</p>
  
  <h3>I have other questions</h3>

  <p>We'll be glad to answer them. Contact us at <strong><a href="mailto:support@muun.com" >support@muun.com</a></strong> 
    to let us know.</p>
</section>

</body>
  `

const contentES = `
<header>
  <div class="title">
    <h1>Kit de Emergencia</h1>
    <date>Creado el {{.CurrentDate}}</date>
  </div>

  <div class="verification-code">
    <h4>Código de Verificación</h4>
    <code>{{.VerificationCode}}</code>
  </div>
</header>

<section>
  <h2>Sobre este documento</h2>

  <p>Aquí encontrarás la información encriptada que necesitas para transferir tu dinero fuera de tu billetera Muun
  sin requerir colaboración de nadie, incluso del software y los servicios de Muun.</p>

  <p>Ésto incluye todas tus claves privadas (encriptadas de forma segura con tu Recovery Code) y algo de información
  adicional relacionada a tu billetera.</p>

  <p>Con éste documento y to Código de Recuperación a mano, tienes posesión total de tu dinero. Nadie más tiene
  todas las piezas. Bitcoin fue creado para esto: darle a la gente control total sobre el dinero que les pertenece.</p>
</section>

<section>
  <h2>Recuperando tu dinero</h2>

  <p>Para proceder con la transferencia de tus fondos, recomendamos usar nuestra
    <a href="https://github.com/muun/recovery">Herramienta de Recuperación de código abierto</a>. Está disponible para que
    todo el mundo la descargue y la examine, y siempre lo estará.</p>

  <p>La creamos para asistirte en el proceso, pero nada te impide hacerlo manualmente si prefieres.</p>

  <p>Entra en <strong>github.com/muun/recovery</strong> y sigue las instrucciones para transferir tu dinero a una
  dirección de Bitcoin que elijas.</p>
</section>

<section>
  <h2>Información de recuperación</h2>

  <p>Ésto es lo que necesitas para la transferencia, además de tu Código de Recuperación. Si éstos códigos te parecen
  confusos, no te precupes: la <a href="https://github.com/muun/recovery">Herramienta de Recuperación</a> se hará cargo
  de todo</p>
</section>

<section>
  <h3>Primera Clave Privada Encriptada</h3>
  <div class="data">{{.FirstEncryptedKey}}</div>

  <h3>Segunda Clave Privada Encriptada</h3>
  <div class="data">{{.SecondEncryptedKey}}</div>
  
  <h3>Descriptores de outputs</h3>
  <div class="data">
    sh(wsh(multi(2, <span class="key-placeholder">primera clave</span>/1'/1'/0/*, <span class="key-placeholder">segunda clave</span>/1'/1'/0/*)))<br>
    sh(wsh(multi(2, <span class="key-placeholder">primera clave</span>/1'/1'/1/*, <span class="key-placeholder">segunda clave</span>/1'/1'/1/*)))<br>
    sh(wsh(multi(2, <span class="key-placeholder">primera clave</span>/1'/1'/2/*/*, <span class="key-placeholder">segunda clave</span>/1'/1'/2/*/*)))<br>
    wsh(multi(2, <span class="key-placeholder">primera clave</span>/1'/1'/0/*, <span class="key-placeholder">segunda clave</span>/1'/1'/0/*))<br>
    wsh(multi(2, <span class="key-placeholder">primera clave</span>/1'/1'/1/*, <span class="key-placeholder">segunda clave</span>/1'/1'/1/*))<br>
    wsh(multi(2, <span class="key-placeholder">primera clave</span>/1'/1'/2/*/*, <span class="key-placeholder">segunda clave</span>/1'/1'/2/*/*))
  </div>
</section>

<section class="page-break-before">
  <h2>Algunas preguntas que puedes tener</h2>

  <h3>¿Puedo imprimir éste documento?</h3>

  <p>Puedes, pero recomendamos almacenarlo online, en algún servicio como Google Drive, iCloud, OneDrive o Dropbox.
    Éstos proveedores se han ganado la confianza de sus usuarios por estar siempre disponibles y custodiar su información
    con fuertes prácticas de seguridad. También son gratuitos.</p>

  <p>Si decides imprimirlos, asegúrate de guardarlos en algún lugar seguro y lejos de tu Código de Recuperación. Recuerda:
  una persona con ambas piezas puede tomar control de tus fondos.</p>

  <h3>¿Qué pasa si pierdo mi Kit de Emergencia?</h3>

  <p>No te preocupes. Tu dinero no está perdido. Está todo ahí, en la blockchain de Bitcoin, esperándote. Usa nuestras
  aplicaciones de Android o iOS y crea un nuevo Kit en el Centro de Seguridad.</p>

  <h3>¿Qué pasa si alguien ve éste documento?</h3>
  
  <p>Mientras tengas tu Código de Recuperación escondido, éste documento es inofensivo. Todo la información que contiene
  está encriptada de forma segura, y sólo tu Código de Recuperación puede desencriptarla para poder usarla.</p>
  
  <p>Aún así, recomendamos que lo guardes donde sólo tú puedes verlo. Si realmente te preocupa perderlo o quieres 
  compartirlo con alguien por otra razón, sólo hazlo con gente de plena confianza.</p>
  
  <h3>¿Por qué no tengo mi mnemonic?</h3>
  
  <p>Si estás familiarizado con Bitcoin, probablemente hayas visto las mnemonics y aprendido a confiar en ellas.
  Al día de hoy, muchas billeteras utilizan esa técnica.</p>

  <p>Las mnemonics no tienen ningún problema intrínseco, pero han quedado obsoletas. Las doce palabras no son suficientes para codificar
  toda la información que una billetera moderna de Bitcoin necesita para funcionar, y el problema sólo se pondrá peor
  a medida que la tecnología avance. Ya hay mejoras encaminadas que harían la recuperación con mnemonics no sólo
  difícil, sino imposible.</p>

  <p>Por eso decidimos garantizar la posesión completa con un método más seguro, flexible y capaz de evolucionar.
  De ésta manera, podremos seguir mejorando nuestra tecnología y continuar modernizando nuestro software.</p>

  <h3>Tengo otras preguntas</h3>

  <p>Siempre estamos disponibles para contestarlas. Contáctanos a <strong><a href="mailto:support@muun.com" >support@muun.com</a></strong>
  y te ayudaremos.</p>
</section>

</body>
  `
