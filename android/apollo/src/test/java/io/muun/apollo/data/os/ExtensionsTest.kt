package io.muun.apollo.data.os

import io.muun.apollo.data.toSafeAscii
import org.junit.Assert
import org.junit.Test

class ExtensionsTest {

    private val languages = arrayListOf(
        "ascii: Hello word!!",
        "japanese: ドメイン名例",
        "japaneseWithAscii: MajiでKoiする5秒前",
        "already encoded japanese: \u30c9\u30e1\u30a4\u30f3\u540d\u4f8b",
        "korean: 도메인",
        "thai: ยจฆฟคฏข",
        "russian: правда",
        "emoji: 😉",
        "non encoded: \\\\ud83d9",
        "non-ascii mixup: 「 Б ü æ α 例 αβγ 」",
    ).joinToString("\n")

    private val expectedEncodedLanguages = "ascii: Hello word!!\n" +
        "japanese: \\u30c9\\u30e1\\u30a4\\u30f3\\u540d\\u4f8b\n" +
        "japaneseWithAscii: Maji\\u3067Koi\\u3059\\u308b5\\u79d2\\u524d\n" +
        "already encoded japanese: \\u30c9\\u30e1\\u30a4\\u30f3\\u540d\\u4f8b\n" +
        "korean: \\ub3c4\\uba54\\uc778\n" +
        "thai: \\u0e22\\u0e08\\u0e06\\u0e1f\\u0e04\\u0e0f\\u0e02\n" +
        "russian: \\u043f\\u0440\\u0430\\u0432\\u0434\\u0430\n" +
        "emoji: \\ud83d\\ude09\n" +
        "non encoded: \\\\ud83d9\n" +
        "non-ascii mixup: \\u300c \\u0411 \\u00fc \\u00e6 \\u03b1 \\u4f8b \\u03b1\\u03b2\\u03b3 \\u300d"

    private val actualResponse = "{\n" +
        "  \"epochInMilliseconds\": 1734567549279,\n" +
        "  \"batteryLevel\": 92,\n" +
        "  \"maxBatteryLevel\": 100,\n" +
        "  \"batteryHealth\": \"GOOD\",\n" +
        "  \"batteryDischargePrediction\": -1,\n" +
        "  \"batteryState\": \"UNPLUGGED\",\n" +
        "  \"totalInternalStorage\": 3087986688,\n" +
        "  \"freeInternalStorage\": 866512896,\n" +
        "  \"freeExternalStorage\": [\n" +
        "    530092032,\n" +
        "    75595776\n" +
        "  ],\n" +
        "  \"totalExternalStorage\": [\n" +
        "    18224549888,\n" +
        "    2438987776\n" +
        "  ],\n" +
        "  \"totalRamStorage\": 1922134016,\n" +
        "  \"freeRamStorage\": 519774208,\n" +
        "  \"dataState\": \"DATA_DISCONNECTED\",\n" +
        "  \"simStates\": [\n" +
        "    \"SIM_STATE_READY\",\n" +
        "    \"SIM_STATE_READY\"\n" +
        "  ],\n" +
        "  \"networkTransport\": \"WIFI\",\n" +
        "  \"androidUptimeMillis\": 450455711,\n" +
        "  \"androidElapsedRealtimeMillis\": 787550180,\n" +
        "  \"androidBootCount\": 980,\n" +
        "  \"language\": \"es_ES\",\n" +
        "  \"timeZoneOffsetInSeconds\": -14400,\n" +
        "  \"telephonyNetworkRegion\": \"VE\",\n" +
        "  \"simRegion\": \"ve\",\n" +
        "  \"appDataDir\": \"/data/user/0/io.muun.apollo\",\n" +
        "  \"vpnState\": 0,\n" +
        "  \"appImportance\": 230,\n" +
        "  \"displayMetrics\": {\n" +
        "    \"density\": 1.75,\n" +
        "    \"densityDpi\": 280,\n" +
        "    \"widthPixels\": 720,\n" +
        "    \"heightPixels\": 1422,\n" +
        "    \"xdpi\": 281.353,\n" +
        "    \"ydpi\": 283.028\n" +
        "  },\n" +
        "  \"usbConnected\": 0,\n" +
        "  \"usbPersistConfig\": \"mtp\",\n" +
        "  \"bridgeEnabled\": 0,\n" +
        "  \"bridgeDaemonStatus\": \"stopped\",\n" +
        "  \"developerEnabled\": 1,\n" +
        "  \"proxyHttp\": \"\",\n" +
        "  \"proxyHttps\": \"\",\n" +
        "  \"proxySocks\": \"\",\n" +
        "  \"autoDateTime\": 1,\n" +
        "  \"autoTimeZone\": 1,\n" +
        "  \"timeZoneId\": \"America/Caracas\",\n" +
        "  \"androidDateFormat\": \"d/M/yy\",\n" +
        "  \"regionCode\": \"ES\",\n" +
        "  \"androidCalendarIdentifier\": \"gregory\",\n" +
        "  \"androidMobileRxTraffic\": 0,\n" +
        "  \"androidSimOperatorId\": \"73404\",\n" +
        "  \"androidSimOperatorName\": \"Corporación Digitel\",\n" +
        "  \"androidMobileOperatorId\": \"73402\",\n" +
        "  \"mobileOperatorName\": \"DIGITEL\",\n" +
        "  \"androidMobileRoaming\": false,\n" +
        "  \"androidMobileDataStatus\": 0,\n" +
        "  \"androidMobileRadioType\": 1,\n" +
        "  \"androidMobileDataActivity\": 2,\n" +
        "  \"androidNetworkLink\": {\n" +
        "    \"interfaceName\": \"wlan0\",\n" +
        "    \"routesSize\": 3,\n" +
        "    \"routesInterfaces\": [\n" +
        "      \"wlan0\"\n" +
        "    ],\n" +
        "    \"hasGatewayRoute\": 1,\n" +
        "    \"dnsAddresses\": [\n" +
        "      \"192.168.0.1\"\n" +
        "    ],\n" +
        "    \"linkHttpProxyHost\": \"\"\n" +
        "  }\n" +
        "}"

    private val expectedEncodedResponse = "{\n" +
        "  \"epochInMilliseconds\": 1734567549279,\n" +
        "  \"batteryLevel\": 92,\n" +
        "  \"maxBatteryLevel\": 100,\n" +
        "  \"batteryHealth\": \"GOOD\",\n" +
        "  \"batteryDischargePrediction\": -1,\n" +
        "  \"batteryState\": \"UNPLUGGED\",\n" +
        "  \"totalInternalStorage\": 3087986688,\n" +
        "  \"freeInternalStorage\": 866512896,\n" +
        "  \"freeExternalStorage\": [\n" +
        "    530092032,\n" +
        "    75595776\n" +
        "  ],\n" +
        "  \"totalExternalStorage\": [\n" +
        "    18224549888,\n" +
        "    2438987776\n" +
        "  ],\n" +
        "  \"totalRamStorage\": 1922134016,\n" +
        "  \"freeRamStorage\": 519774208,\n" +
        "  \"dataState\": \"DATA_DISCONNECTED\",\n" +
        "  \"simStates\": [\n" +
        "    \"SIM_STATE_READY\",\n" +
        "    \"SIM_STATE_READY\"\n" +
        "  ],\n" +
        "  \"networkTransport\": \"WIFI\",\n" +
        "  \"androidUptimeMillis\": 450455711,\n" +
        "  \"androidElapsedRealtimeMillis\": 787550180,\n" +
        "  \"androidBootCount\": 980,\n" +
        "  \"language\": \"es_ES\",\n" +
        "  \"timeZoneOffsetInSeconds\": -14400,\n" +
        "  \"telephonyNetworkRegion\": \"VE\",\n" +
        "  \"simRegion\": \"ve\",\n" +
        "  \"appDataDir\": \"/data/user/0/io.muun.apollo\",\n" +
        "  \"vpnState\": 0,\n" +
        "  \"appImportance\": 230,\n" +
        "  \"displayMetrics\": {\n" +
        "    \"density\": 1.75,\n" +
        "    \"densityDpi\": 280,\n" +
        "    \"widthPixels\": 720,\n" +
        "    \"heightPixels\": 1422,\n" +
        "    \"xdpi\": 281.353,\n" +
        "    \"ydpi\": 283.028\n" +
        "  },\n" +
        "  \"usbConnected\": 0,\n" +
        "  \"usbPersistConfig\": \"mtp\",\n" +
        "  \"bridgeEnabled\": 0,\n" +
        "  \"bridgeDaemonStatus\": \"stopped\",\n" +
        "  \"developerEnabled\": 1,\n" +
        "  \"proxyHttp\": \"\",\n" +
        "  \"proxyHttps\": \"\",\n" +
        "  \"proxySocks\": \"\",\n" +
        "  \"autoDateTime\": 1,\n" +
        "  \"autoTimeZone\": 1,\n" +
        "  \"timeZoneId\": \"America/Caracas\",\n" +
        "  \"androidDateFormat\": \"d/M/yy\",\n" +
        "  \"regionCode\": \"ES\",\n" +
        "  \"androidCalendarIdentifier\": \"gregory\",\n" +
        "  \"androidMobileRxTraffic\": 0,\n" +
        "  \"androidSimOperatorId\": \"73404\",\n" +
        "  \"androidSimOperatorName\": \"Corporaci\\u00f3n Digitel\",\n" +
        "  \"androidMobileOperatorId\": \"73402\",\n" +
        "  \"mobileOperatorName\": \"DIGITEL\",\n" +
        "  \"androidMobileRoaming\": false,\n" +
        "  \"androidMobileDataStatus\": 0,\n" +
        "  \"androidMobileRadioType\": 1,\n" +
        "  \"androidMobileDataActivity\": 2,\n" +
        "  \"androidNetworkLink\": {\n" +
        "    \"interfaceName\": \"wlan0\",\n" +
        "    \"routesSize\": 3,\n" +
        "    \"routesInterfaces\": [\n" +
        "      \"wlan0\"\n" +
        "    ],\n" +
        "    \"hasGatewayRoute\": 1,\n" +
        "    \"dnsAddresses\": [\n" +
        "      \"192.168.0.1\"\n" +
        "    ],\n" +
        "    \"linkHttpProxyHost\": \"\"\n" +
        "  }\n" +
        "}"

    @Test
    fun toSafeAsciiExtensionTest() {
        val encodedLanguages = languages.toSafeAscii()

        Assert.assertEquals(encodedLanguages, expectedEncodedLanguages)
    }

    @Test
    fun realResponseToSafeAsciiExtensionTest() {
        val encodedResponse = actualResponse.toSafeAscii()

        Assert.assertEquals(encodedResponse, expectedEncodedResponse)
    }
}