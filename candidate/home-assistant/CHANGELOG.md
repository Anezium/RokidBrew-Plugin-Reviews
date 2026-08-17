# Changelog

## 0.2.2

- Add password-encrypted portable export and import for Home Assistant credentials,
  including the long-lived access token, and the complete editable Nexus dashboard.
- Validate imported pages, widgets, actions, bindings, and context rules before replacing
  the active configuration.

## 0.2.1

- Replace the opinionated starter dashboard with a neutral setup guide.
- Remove legacy built-in starter pages and rules while preserving user-created pages.
- Keep the in-app unofficial community-plugin disclosure.
- Build against the latest published Nexus SDK (`sdk-v0.15.0`).

## 0.2.0

- Add the phone-only Home Assistant dashboard plugin for Rokid Nexus.
- Store Home Assistant credentials with Android Keystore.
- Configure pages, widgets, actions, and priority-based context rules.
- Render live values as typed Nexus cards and execute selected Home Assistant actions.
