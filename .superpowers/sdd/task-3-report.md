STATUS: DONE

COMMIT: 8579502

TEST_RESULTS:
ZerodhaAuthControllerTest: 9/9 PASS (8 pre-existing + 1 new test)
Full suite: 203 total — pre-existing UserControllerTest failures unchanged (10 context-load errors); ZerodhaAuthControllerTest 9/9 PASS

CHANGES:
- ZerodhaAuthController.java: added isMobileClient() helper; callback() now branches on User-Agent containing "ZerodhaBreakoutMobile" to redirect to zbs://zerodha-callback?status=connected instead of web URL
- ZerodhaAuthControllerTest.java: added callback_redirectsToDeepLinkForMobileClient test

CONCERNS: none
