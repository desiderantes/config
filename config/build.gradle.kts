plugins {
	`java-library`
	kotlin("jvm") version libs.versions.kotlin

}

java {
	toolchain {
		languageVersion = libs.versions.java.map(JavaLanguageVersion::of)
	}
	sourceCompatibility = JavaVersion.toVersion(libs.versions.java.get())
	targetCompatibility = JavaVersion.toVersion(libs.versions.java.get())
}

dependencies {
	api(libs.jetbrains.annotations)

	testImplementation(kotlin("stdlib-jdk8", libs.versions.kotlin.get()))
	testImplementation(libs.moshi.asProvider())
	testImplementation(libs.moshi.ast)


	// Use junit5 for testing our library
	testImplementation(kotlin("test-junit5", libs.versions.kotlin.get()))
	testImplementation(libs.junit5)
	testImplementation(libs.junit.platform.engine)
	testImplementation(libs.junit.platform.launcher)
	testImplementation(libs.junit.platform.runner)
	testImplementation(libs.junit.platform.suite.api)
	testImplementation(project(":testlib-resource-hack"))
}

tasks.named<Test>("test") {
	environment(
		"testList.0" to "0",
		"testList.1" to "1",
		"CONFIG_FORCE_b" to "5",
		"CONFIG_FORCE_testList_0" to "10",
		"CONFIG_FORCE_testList_1" to "11",
		"CONFIG_FORCE_42___a" to "1",
		"CONFIG_FORCE_a_b_c" to "2",
		"CONFIG_FORCE_a__c" to "3",
		"CONFIG_FORCE_a___c" to "4",
		"CONFIG_FORCE_akka_version" to "foo",
		"CONFIG_FORCE_akka_event__handler__dispatcher_max__pool__size" to "10",
		"SECRET_A" to "A", // ConfigTest.renderShowEnvVariableValues
		"SECRET_B" to "B", // ConfigTest.renderShowEnvVariableValues
		"SECRET_C" to "C" // ConfigTest.renderShowEnvVariableValues
	)

	systemProperties("config.trace" to "loads")
	useJUnitPlatform()
}


