package edu.uci.eecs.crowdsafe.common.util;

import java.util.HashMap;
import java.util.Map;

public enum RiskySystemCall {
	ZwDeviceIoControlFile(0x4, "ZwDeviceIoControlFile"),
	ZwWriteFile(0x5, "ZwWriteFile"),
	NtWriteFileGather(0x18, "NtWriteFileGather"),
	ZwSetInformationProcess(0x19, "ZwSetInformationProcess"),
	ZwCreateKey(0x1A, "ZwCreateKey"),
	ZwOpenProcess(0x23, "ZwOpenProcess"),
	NtSetInformationFile(0x24, "NtSetInformationFile"),
	NtTerminateProcess(0x29, "NtTerminateProcess"),
	ZwFsControlFile(0x36, "ZwFsControlFile"),
	ZwCreateProcessEx(0x4A, "ZwCreateProcessEx"),
	ZwCreateThread(0x4B, "ZwCreateThread"),
	ZwProtectVirtualMemory(0x4D, "ZwProtectVirtualMemory"),
	ZwResumeThread(0x4F, "ZwResumeThread"),
	ZwCreateFile(0x52, "ZwCreateFile"),
	ZwSetValueKey(0x5D, "ZwSetValueKey"),
	NtAddBootEntry(0x66, "NtAddBootEntry"),
	ZwAddDriverEntry(0x67, "ZwAddDriverEntry"),
	ZwAlertResumeThread(0x69, "ZwAlertResumeThread"),
	ZwCreateProcess(0x9F, "ZwCreateProcess"),
	ZwCreateThreadEx(0xA5, "ZwCreateThreadEx"),
	NtCreateUserProcess(0xAA, "NtCreateUserProcess"),
	ZwDeleteBootEntry(0xB0, "ZwDeleteBootEntry"),
	ZwDeleteDriverEntry(0xB1, "ZwDeleteDriverEntry"),
	ZwDeleteFile(0xB2, "ZwDeleteFile"),
	NtDeleteKey(0xB3, "NtDeleteKey"),
	ZwDeleteValueKey(0xB6, "ZwDeleteValueKey"),
	ZwLoadKey(0xDD, "ZwLoadKey"),
	ZwModifyBootEntry(0xE8, "ZwModifyBootEntry"),
	ZwModifyDriverEntry(0xE9, "ZwModifyDriverEntry"),
	ZwRaiseException(0x12F, "ZwRaiseException"),
	ZwRaiseHardError(0x130, "ZwRaiseHardError"),
	ZwRenameKey(0x13B, "ZwRenameKey"),
	ZwReplaceKey(0x13D, "ZwReplaceKey"),
	ZwRestoreKey(0x143, "ZwRestoreKey"),
	NtResumeProcess(0x144, "NtResumeProcess"),
	NtSaveKey(0x149, "NtSaveKey"),
	NtSetBootEntryOrder(0x14E, "NtSetBootEntryOrder"),
	ZwSetBootOptions(0x14F, "ZwSetBootOptions"),
	ZwSetDriverEntryOrder(0x155, "ZwSetDriverEntryOrder"),
	NtSetEaFile(0x156, "NtSetEaFile"),
	NtSetSecurityObject(0x169, "NtSetSecurityObject"),
	NtSetSystemEnvironmentValue(0x16A, "NtSetSystemEnvironmentValue"),
	ZwSetSystemEnvironmentValueEx(0x16B, "ZwSetSystemEnvironmentValueEx"),
	NtSetSystemInformation(0x16C, "NtSetSystemInformation"),
	ZwSetSystemPowerState(0x16D, "ZwSetSystemPowerState"),
	ZwSetSystemTime(0x16E, "ZwSetSystemTime"),
	ZwShutdownSystem(0x174, "ZwShutdownSystem"),
	NtSuspendProcess(0x17A, "NtSuspendProcess"),
	NtSuspendThread(0x17B, "NtSuspendThread"),
	NtUmsThreadYield(0x183, "NtUmsThreadYield"),
	ZwUnloadDriver(0x184, "ZwUnloadDriver"),
	ZwUnloadKey(0x185, "ZwUnloadKey");

	public static final Map<Integer, RiskySystemCall> sysnumMap = createSysnumMap();

	public final int sysnum;
	public final String name;

	private RiskySystemCall(int sysnum, String name) {
		this.sysnum = sysnum;
		this.name = name;
	}

	private static Map<Integer, RiskySystemCall> createSysnumMap() {
		Map<Integer, RiskySystemCall> sysnumMap = new HashMap<Integer, RiskySystemCall>();
		for (RiskySystemCall syscall : RiskySystemCall.values()) {
			sysnumMap.put(syscall.sysnum, syscall);
		}
		return sysnumMap;
	}
}
