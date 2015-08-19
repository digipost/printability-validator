# Digipost Printability Validator

Formålet med dette biblioteket er å gi avsendere mulighet til å validere om et PDF dokument kan skrives ut av Digiposts utskriftstjeneste. Følgende valideringsregler eksisterer i biblioteket:

1. Det må eksistere en venstremarg på 18mm i PDF dokumentet.
2. Maks sideantall på PDF dokumentet er 14 sider.
3. Fonter som ikke er standard fonter må embeddes i PDF dokumentet.
4. PDF versjon må være fra 1.0 til 1.5

Biblioteket er veldig enkelt å benytte. Flyten består av å instansiere et PdfValidator object, sende inn konfigurasjon for hvilke regler som skal valideres og til slutt kjøre valideringen. Du får tilbake et PdfValidationResult object som inkluderer:

* antall sider
* liste med eventuelle valideringsfeil
* om PDF dokumentet er ok for print
* om PDF dokumentet er ok for digital distribusjon

Pseudo-kode eksempel:

```
PdfValidator pdfValidator = new PdfValidator();
// Alle valideringsregler er slått på.
PdfValidationSettings printValideringsinnstillinger = new PdfValidationSettings (true, true, true, true);
PdfValidationResult pdfValidationResult =  pdfValidator.validate(pdf, printValideringsinnstillinger);
```

Krav til PDF dokumenter er tilgjengelig på https://www.digipost.no/plattform/annet/print

## For avsendere som sender til utskrift via offentlig Sikker Digital Post (SDP) meldingsformidler tjeneste.

I SDP utskriftstjenesten er det satt opp en felles valideringskonfigurasjon. Den er som følger:

´´´
PdfValidationSettings printValideringsinnstillinger = new PdfValidationSettings(false, true, false, true);
´´´

[![Build Status](https://travis-ci.org/digipost/printability-validator.svg)](https://travis-ci.org/digipost/printability-validator)
