# AndroidManifest.xml Additions Required

Add the following service declaration to your `app/src/main/AndroidManifest.xml` file inside the `<application>` tag:

```xml
<!-- Google Play Billing to Firestore Sync Service -->
<service
    android:name=".billing.BillingSyncService"
    android:enabled="true"
    android:exported="false" />
```

## Complete Application Tag Example

Your application tag should look like this:

```xml
<application
    android:name=".MyApplication"
    android:allowBackup="true"
    android:icon="@mipmap/ic_launcher"
    android:label="@string/app_name"
    android:theme="@style/AppTheme">
    
    <!-- Your existing activities -->
    <activity android:name=".MainActivity" />
    <activity android:name=".profile.ProfileActivity" />
    <!-- ... other activities ... -->
    
    <!-- Add this service -->
    <service
        android:name=".billing.BillingSyncService"
        android:enabled="true"
        android:exported="false" />
        
</application>
```

## WorkManager Dependencies

Ensure your `app/build.gradle.kts` includes WorkManager dependency:

```kotlin
dependencies {
    // ... existing dependencies ...
    implementation("androidx.work:work-runtime-ktx:2.8.1")
}
```

This is required for the background billing sync functionality.