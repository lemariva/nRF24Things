nRF24Things: Android Things Project
=====================================

This project enables to use the nrf24l01+ modules with Android Things. In this project the classes RF24, RF24Network and RF24Mesh (developed by <a href="http://tmrh20.github.io/">TMRH20</a> and <a href="https://github.com/maniacbug" target="_blank">maniacbug</a>) were re-implemented in Java.

Please refer to https://goo.gl/XsRJtI for more information.

Pre-requisites
--------------

- Raspberry Pi v3 (<a href="https://rover.ebay.com/rover/1/707-53477-19255-0/1?icep_id=114&ipn=icep&toolid=20004&campid=5338002758&mpre=http%3A%2F%2Fwww.ebay.de%2Fsch%2Fi.html%3F_from%3DR40%26_trksid%3Dp2050601.m570.l1313.TR0.TRC0.H0.Xraspberry%2Bpi.TRS0%26_nkw%3Draspberry%2Bpi%26_sacat%3D0">
ebay
</a>, <a href="amazon.de">amazon.de</a>)
- <a href="https://rover.ebay.com/rover/1/707-53477-19255-0/1?icep_id=114&ipn=icep&toolid=20004&campid=5338002758&mpre=http%3A%2F%2Fwww.ebay.de%2Fsch%2Fi.html%3F_odkw%3Draspberry%2Bpi%26_osacat%3D0%26_from%3DR40%26_trksid%3Dp2045573.m570.l1313.TR0.TRC0.H0.Xrf24l01%252B.TRS0%26_nkw%3Dnrf24l01%252B%26_sacat%3D0">nrf24l01+</a>
- <a href="https://developer.android.com/studio/index.html" target="_blank"> Android Studio (>2.2.3) </a> 
- <a href="https://developer.android.com/things/preview/download.html" target="_blank"> Android Things (>(0.)4.1)</a>


Build and install
--------------------

On Android Studio, click on the "Run" button.

If you prefer to run on the command line, type

```bash
./gradlew installDebug
adb shell am start com.lemariva.androidthings.rf24/.MainActivity
```

Credit
--------------------
Based on:

Libraries from <a href="https://github.com/TMRh20" target="_blank">TMRh20</a> and <a href="https://github.com/maniacbug" target="_blank">maniacbug</a>:
* <a href="https://github.com/TMRh20/RF24" target="_blank">RF24</a>
* <a href="https://github.com/TMRh20/RF24Network" target="_blank">RF24Network</a>
* <a href="https://github.com/TMRh20/RF24Mesh" target="_blank">RF24Mesh</a>

More Info & Help
--------------------
* Blog article: https://goo.gl/XsRJtI
* Almost all classes, methods and properties have a help description.
* The github documentation written by TMRH20: 
	* http://tmrh20.github.io/RF24 
	* http://tmrh20.github.io/RF24Network/
	* https://tmrh20.github.io/RF24Mesh
* Android Things documentation: 
	* https://developer.android.com/things/sdk/index.html
 
Changelog
-------------------
* Revision: 0.2v
 
Licenses
--------------------
Licenses include, but are not limited to the following (check respective files): 
* GNU 
* Apache
* etc...
