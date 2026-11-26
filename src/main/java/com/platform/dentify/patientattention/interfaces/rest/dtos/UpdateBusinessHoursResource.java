package com.platform.dentify.patientattention.interfaces.rest.dtos;

import java.util.List;

public record UpdateBusinessHoursResource(List<BusinessDayResource> days) {
}
