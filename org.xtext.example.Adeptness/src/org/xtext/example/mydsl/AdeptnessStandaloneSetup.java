/*
 * generated by Xtext 2.21.0
 */
package org.xtext.example.mydsl;


/**
 * Initialization support for running Xtext languages without Equinox extension registry.
 */
public class AdeptnessStandaloneSetup extends AdeptnessStandaloneSetupGenerated {

	public static void doSetup() {
		new AdeptnessStandaloneSetup().createInjectorAndDoEMFRegistration();
	}
}
