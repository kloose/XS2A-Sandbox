import { Component, OnInit } from '@angular/core';
import { AspspService } from 'src/services/aspsp.service';
import { ConsentTypes } from '../../../../../models/consentTypes.model';
import { JsonService } from '../../../../../services/json.service';
import { SpinnerVisibilityService } from 'ng-http-loader';
import { combineLatest } from 'rxjs';

@Component({
  selector: 'app-rdct-consent-post',
  templateUrl: './rdct-consent-post.component.html',
})
export class RdctConsentPOSTComponent implements OnInit {
  activeSegment = 'documentation';
  consentTypes: ConsentTypes;
  jsonData: object;
  headers: object = {
    'X-Request-ID': '2f77a125-aa7a-45c0-b414-cea25a116035',
    'TPP-Explicit-Authorisation-Preferred': 'true',
    'PSU-ID': 'YOUR_USER_LOGIN',
    'PSU-IP-Address': '1.1.1.1',
    'TPP-Redirect-Preferred': 'true',
    'TPP-Redirect-URI': 'https://adorsys-platform.de/solutions/xs2a-sandbox/',
    'TPP-Nok-Redirect-URI': 'https://www.google.com',
  };
  body;

  constructor(
    private aspsp: AspspService,
    private jsonService: JsonService,
    private spinner: SpinnerVisibilityService
  ) {
    this.fetchJsonData();
  }

  changeSegment(segment) {
    if (segment === 'documentation' || segment === 'play-data') {
      this.activeSegment = segment;
    }
  }

  ngOnInit() {}

  fetchJsonData() {
    this.spinner.show();

    const results = combineLatest(
      this.jsonService.getPreparedJsonData(
        this.jsonService.jsonLinks.dedicatedAccountsConsent
      ),
      this.jsonService.getPreparedJsonData(
        this.jsonService.jsonLinks.bankOfferedConsent
      ),
      this.jsonService.getPreparedJsonData(
        this.jsonService.jsonLinks.globalConsent
      ),
      this.jsonService.getPreparedJsonData(
        this.jsonService.jsonLinks.availableAccountsConsent
      ),
      this.jsonService.getPreparedJsonData(
        this.jsonService.jsonLinks.availableAccountsConsentWithBalance
      ),
      this.jsonService.getPreparedJsonData(this.jsonService.jsonLinks.consent)
    );

    results.subscribe(result => {
      this.body = result[0];
      this.jsonData = result[5];
      this.setConsentTypes(result);
      this.spinner.hide();
    });
  }

  setConsentTypes(results: any[]) {
    this.consentTypes = {
      dedicatedAccountsConsent: results[0],
    };

    this.aspsp.getAspspProfile().subscribe(object => {
      const allConsentTypes = object.ais.consentTypes;

      if (allConsentTypes.bankOfferedConsentSupported) {
        this.consentTypes.bankOfferedConsent = results[1];
      }
      if (allConsentTypes.globalConsentSupported) {
        this.consentTypes.globalConsent = results[2];
      }
      if (allConsentTypes.availableAccountsConsentSupported) {
        this.consentTypes.availableAccountsConsent = results[3];
        this.consentTypes.availableAccountsConsentWithBalance = results[4];
      }
    });
  }
}
