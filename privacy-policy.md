# Privacy Policy for VRCM

**Last Updated:** September 24, 2026

## 1. Overview

VRCM is an open-source, third-party companion application for VRChat. VRCM's developers do not operate an analytics, advertising, telemetry, or user-data collection backend for the app.

VRCM must communicate with third-party services to provide its features. This policy explains what remains on your device and what is sent to those services.

## 2. Data stored on your device

Depending on the features you use, VRCM may store the following locally:

- account identifiers, settings, favorite groups, and authenticated VRChat session credentials;
- cached friend, user, world, group, avatar, notification, and gallery information;
- friend activity observed by VRCM, including presence changes, visited worlds, meetings, and time-together summaries;
- friend-network cache data;
- meetup-card configuration, selected local images, and downloaded profile decorations.

Authentication credentials use the platform's secure storage where available. Other application data is stored in VRCM's local app storage. Exported screenshots, gallery images, and meetup cards are written to a location you select or to the system photo gallery.

On iOS, VRCM only asks for permission to add photos to your library when you save an image; it cannot read your existing photos. Images you choose to upload are picked through the system photo picker, which shares only the images you select.

VRCM does not upload locally generated friend-activity statistics or friend-network data to a server operated by the VRCM developers.

## 3. Third-party network communication

VRCM sends requests directly to services needed for the action you request:

- **VRChat services:** authentication, profiles, friends, presence, worlds, instances, groups, notifications, avatars, inventory, and gallery operations, plus any report you choose to submit (the reported content, the category and reason you pick, and optional details) for review by VRChat's moderation team. Requests identify the app by its name, version, and the contact email below, as VRChat requires. Information sent to VRChat is handled under VRChat's own privacy policy and terms.
- **GitHub:** builds distributed through GitHub Releases check the VRCM repository's latest public release. GitHub may receive standard network information such as your IP address and user-agent information. Builds installed from the App Store do not contact GitHub; the App Store delivers their updates.
- **Media hosts referenced by VRChat:** downloading profile images, world images, avatar images, and profile decorations for display or local caching.

When you explicitly upload a gallery image or avatar cover, the selected image is processed on your device and then sent to VRChat. VRCM does not proxy these requests through a VRCM-operated server.

## 4. Clipboard and external links

Clipboard reading is off by default. If you turn it on in Settings, VRCM inspects clipboard text locally when it returns to the foreground, looking for an exact supported VRChat URL or content ID. Unrelated clipboard text is ignored. VRCM asks for confirmation before resolving a clipboard target through the VRChat API.

On supported platforms, a `vrchat.com` link opened from another app can also be handed directly to VRCM. VRCM validates supported official URL formats before requesting the target content.

## 5. Notifications and background monitoring

On Android, optional background friend monitoring communicates with VRChat while the foreground service is active. VRCM can create local system notifications for selected friend activity, requests, group events, Boop events, and VRChat service status. Notification preferences and observed activity remain in local app storage.

Background monitoring is opt-in and can be disabled from VRCM's notification settings. Android notification permission and battery-management behavior are controlled by the operating system.

## 6. Analytics, advertising, and sharing

VRCM contains no developer-operated analytics or advertising service and does not sell personal information. The VRCM developers do not receive the local social statistics described above.

Third-party services contacted by the app process requests under their own policies. Information may also leave VRCM when you deliberately use the system share sheet, save an export to shared storage, or open a link in another application.

## 7. Your controls

You can disable Android background monitoring and individual notification categories, clear VRCM's cache, remove exported images from system storage, or uninstall the app to remove its private local data. Signing out removes the active authenticated session; platform backups may remain subject to your operating-system backup settings.

## 8. Children's privacy

VRCM does not knowingly collect information through a developer-operated service, including information from children. Use of VRChat remains subject to VRChat's age requirements and policies.

## 9. Changes to this policy

If VRCM's data practices change, this policy will be updated with a new revision date.

## 10. Contact

For privacy questions, contact **kamosama.dev@gmail.com**.
