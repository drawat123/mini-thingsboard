package com.minitb.device;

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
import com.minitb.device.DeviceDtos.CreateDeviceRequest;
import com.minitb.device.DeviceDtos.DeviceCredentialsResponse;
import com.minitb.device.DeviceDtos.DeviceResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/devices")
public class DeviceController extends BaseController {

	private final DeviceService deviceService;

	public DeviceController(DeviceService deviceService) {
		this.deviceService = deviceService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DeviceResponse create(@Valid @RequestBody CreateDeviceRequest request) {
		Device device = deviceService.create(getCurrentTenantId(), request.deviceProfileId(), request.name(),
				request.label());
		return DeviceResponse.from(device);
	}

	@GetMapping("/{deviceId}")
	public DeviceResponse get(@PathVariable UUID deviceId) {
		return DeviceResponse.from(deviceService.get(getCurrentTenantId(), deviceId));
	}

	@GetMapping
	public List<DeviceResponse> list() {
		return deviceService.list(getCurrentTenantId()).stream().map(DeviceResponse::from).toList();
	}

	@GetMapping("/{deviceId}/credentials")
	public DeviceCredentialsResponse getCredentials(@PathVariable UUID deviceId) {
		return DeviceCredentialsResponse.from(deviceService.getCredentials(getCurrentTenantId(), deviceId));
	}

	@PostMapping("/{deviceId}/credentials/regenerate")
	public DeviceCredentialsResponse regenerateCredentials(@PathVariable UUID deviceId) {
		return DeviceCredentialsResponse.from(deviceService.regenerateCredentials(getCurrentTenantId(), deviceId));
	}

}
