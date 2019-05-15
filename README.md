# Lightweight Binary Repository

## Using
### Arguments


| Name | Description | Example |
|------|-------------|---------|
|-root|Root directory for repository|`-root=/var/repo`|
|-allowRewriting|Allow or Disallow file rewriting|`-allowRewriting=false`|
|-allowAnonymous|Allow or Disallow Anonymous read access|`-allowAnonymous=true`|
|-bind|Bind address for web serve|`-bind=0.0.0.0:8080` or `-bind=:8080`|
|-admin|Define new Administrator. Can upload change repository. You can define more than one Administrators like `-admin=ad1:ad1 -admin=ad2:ad3`|`-admin=admin:admin123`|
|-guest|Define new Guest. Can only read repository. You can define more than one Guest like `-guest=g1:g2 -guest=g2:g3`|`-guest=nomad:nomad123`|
|-h|Shows command list|`-h`|

### Example
`repo -root=/var/repo -allowRewriting=false -allowAnonymous=true -admin=jenkins:jenkins123`<br>
Sets root directory to `/var/repo` disable rewriting old files and allow get files for all users without Authorization.
Also define one Administrator for upload from jenkins.