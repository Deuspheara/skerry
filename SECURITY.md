# Security

If you find a vulnerability (for example a way to leak a user's token), please report it privately through GitHub's
"Report a vulnerability" button on the Security tab, not in a public issue. Never post tokens, client ids you do not own, or
other people's data.

The apps have no server and keep the sign-in token in the Android Keystore or the Apple Keychain. There is no client secret in this
repository, and CI scans every push and the full history with gitleaks.
