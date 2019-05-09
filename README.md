# Digipost Printability Validator

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/no.digipost/printability-validator/badge.svg)](https://maven-badges.herokuapp.com/maven-central/no.digipost/printability-validator)
[![Build Status](https://travis-ci.org/digipost/printability-validator.svg)](https://travis-ci.org/digipost/printability-validator)

Formålet med dette biblioteket er å gi avsendere mulighet til å validere om et PDF dokument kan skrives ut av Digiposts utskriftstjeneste. Følgende valideringsregler eksisterer i biblioteket:

* Det må eksistere en venstremarg på 15mm i PDF dokumentet.
* Maks sideantall på PDF dokumentet er 14 sider.
* Fonter som ikke er standard fonter må embeddes i PDF dokumentet.
* PDF versjon må være fra 1.0 til 1.5

Biblioteket er veldig enkelt å benytte. Flyten består av å instansiere et PdfValidator object, sende inn konfigurasjon for hvilke regler som skal valideres og til slutt kjøre valideringen. Du får tilbake et PdfValidationResult object som inkluderer:

* Antall sider
* Liste med eventuelle valideringsfeil
* Om PDF dokumentet er ok for print
* Om PDF dokumentet er ok for digital distribusjon

Pseudo-kode eksempel:

```java
PdfValidator pdfValidator = new PdfValidator();
// Alle valideringsregler er slått på.
PdfValidationSettings printValideringsinnstillinger = new PdfValidationSettings (true, true, true, true);
PdfValidationResult pdfValidationResult =  pdfValidator.validate(pdf, printValideringsinnstillinger);
```

Krav til PDF dokumenter er tilgjengelig på https://www.digipost.no/plattform/annet/print

Biblioteket er også tilgjengelig på [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22printability-validator%22).

## For avsendere som sender til utskrift via offentlig Sikker Digital Post (SDP) meldingsformidlertjeneste.

I SDP utskriftstjenesten er det satt opp en felles valideringskonfigurasjon. Den er som følger:

```java
PdfValidationSettings printValideringsinnstillinger = new PdfValidationSettings(false, true, false, true);
```

## Releasing (kun for medlemmer av Digipost organisasjonen)

Se docs/systemer/open-source-biblioteker.md
