package com.minitb.deviceprofile;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.minitb.common.BaseController;
import com.minitb.deviceprofile.DeviceProfileDtos.CreateDeviceProfileRequest;
import com.minitb.deviceprofile.DeviceProfileDtos.DeviceProfileResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/device-profiles")
public class DeviceProfileController extends BaseController {

	private final DeviceProfileService deviceProfileService;

	public DeviceProfileController(DeviceProfileService deviceProfileService) {
		this.deviceProfileService = deviceProfileService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DeviceProfileResponse create(@Valid @RequestBody CreateDeviceProfileRequest request) {
		DeviceProfile profile = deviceProfileService.create(getCurrentTenantId(), request.name(),
				request.description());
		return DeviceProfileResponse.from(profile);
	}

	@GetMapping("/{profileId}")
	public DeviceProfileResponse get(@PathVariable UUID profileId) {
		return DeviceProfileResponse.from(deviceProfileService.get(getCurrentTenantId(), profileId));
	}

	@GetMapping
	public List<DeviceProfileResponse> list() {
		return deviceProfileService.list(getCurrentTenantId()).stream().map(DeviceProfileResponse::from).toList();
	}

}
