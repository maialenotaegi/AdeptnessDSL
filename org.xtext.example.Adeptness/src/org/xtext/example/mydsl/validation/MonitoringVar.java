package org.xtext.example.mydsl.validation;

public class MonitoringVar {
	private String name, type;
	private double max, min;

	public MonitoringVar(String name, String type, double max, double min) {
		super();
		this.name = name;
		this.type = type;
		this.max = max;
		this.min = min;
	}

	public String getName() {
		return this.name;
	}

	public String getType() {
		return this.type;
	}

	public double getMax() {
		return this.max;
	}

	public double getMin() {
		return this.min;
	}

	public void update(String type, double max, double min) {
		this.type = type;
		this.max = max;
		this.min = min;
	}

}