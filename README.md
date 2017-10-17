# SHT2x driver for Android Things
Android Things driver for Sensirion [STH2x][sht2x] series humidity and temperature sensor

NOTE: these drivers are not production-ready. They are offered as sample implementations of Android Things user space drivers for common peripherals as part of the Developer Preview release. There is no guarantee of correctness, completeness or robustness.

How to use the driver
---------------------

### Gradle dependency
To use the this driver, simply add the line below to your module's `build.gradle`.

```
dependencies {
    compile 'com.wusp.androidthings:sht2x:0.0.3'
}
```

This driver also need [kotlinx.coroutines][kotlinx.coroutines], add kotlinx.coroutines to module's `build.gradle` too.

```
dependencies {
    compile 'org.jetbrains.kotlinx:kotlinx-coroutines-core:0.17'
}
```

### Sample usage
```
//Using i2c bus name to initialize SHT2x sensor
val p = PeripheralManagerService()
val device = SHT2x(p.i2cBusList[0])

val job = runBlocking {
	try {
    	//read the humidity in not hold mode.
       	val hData = device.readRHinNotHold()
       	if (hData != null) {
       		val humidity = convertToHumidity(hData)
       	}
       	delay(500)      //delay to execute
       	//read the temperature in hold mode
       	val tData = device.readTinHold()
       	if (tData != null) {
             val temperature = convertToTemperature(tData)
       	}
	} catch (e: Exception) {
    	e.printStackTrace()
    } finally {
       device.close()  //close device to release resource
    }
}
```


License
-------

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements.  See the NOTICE file distributed with this work for
additional information regarding copyright ownership.  The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License.  You may obtain a copy of
the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
License for the specific language governing permissions and limitations under
the License.

[sht2x]: https://www.sensirion.com/fileadmin/user_upload/customers/sensirion/Dokumente/2_Humidity_Sensors/Sensirion_Humidity_Sensors_SHT20_Datasheet_V4.pdf
[kotlinx.coroutines]: https://github.com/Kotlin/kotlinx.coroutines