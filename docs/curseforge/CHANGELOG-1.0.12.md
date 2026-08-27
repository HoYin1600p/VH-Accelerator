## 1.0.12 - Update Notifications

**Added**

- Added a lightweight update notice on the main menu when a newer VH
  Accelerator release is available.
- Added an occasional in-game reminder with a clickable CurseForge link.
- Reminder frequency is counted once per eligible client launch, so server
  transfers and additional world joins cannot cause extra reminders.
- Added `/vha updates on|off|status` and the `checkForUpdates` client option.
  Turning it off immediately cancels the check and hides update notices.

**Changed**

- The permanent main-menu timer now shows launch time only.
- Routine timing messages are disabled by default for new installations.
- Update reminder state is saved shortly after the first playable world frame
  instead of during it.

[Read the detailed GitHub changelog](https://github.com/HoYin1600p/VH-Accelerator/releases/tag/v1.0.12)
