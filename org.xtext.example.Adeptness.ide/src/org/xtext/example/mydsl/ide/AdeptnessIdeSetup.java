/*
 * generated by Xtext 2.21.0
 */
package org.xtext.example.mydsl.ide;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.eclipse.xtext.util.Modules2;
import org.xtext.example.mydsl.AdeptnessRuntimeModule;
import org.xtext.example.mydsl.AdeptnessStandaloneSetup;

/**
 * Initialization support for running Xtext languages as language servers.
 */
public class AdeptnessIdeSetup extends AdeptnessStandaloneSetup {

	@Override
	public Injector createInjector() {
		return Guice.createInjector(Modules2.mixin(new AdeptnessRuntimeModule(), new AdeptnessIdeModule()));
	}
	
}