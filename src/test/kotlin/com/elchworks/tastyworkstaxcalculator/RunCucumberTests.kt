package com.elchworks.tastyworkstaxcalculator

import org.junit.platform.suite.api.IncludeEngines
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite
import org.springframework.boot.test.context.SpringBootTest

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("bdd")
class RunCucumberTests {
}