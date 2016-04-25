/*
 * Copyright (C) 2016 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cyanogenmod.setupwizard.ui;

import com.cyanogenmod.setupwizard.R;
import com.cyanogenmod.setupwizard.util.SetupWizardUtils;

import android.annotation.Nullable;
import android.content.Intent;
import android.os.Bundle;

import static com.cyanogenmod.setupwizard.ui.WizardManager.EXTRA_SCRIPT_URI;

public class WizardActivity extends SetupActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            onSetupStart();
            Intent intent = new Intent(WizardManager.ACTION_LOAD);
            if (SetupWizardUtils.isOwner()) {
                intent.putExtra(EXTRA_SCRIPT_URI, getString(R.string.cm_wizard_script_uri));
            } else {
                intent.putExtra(EXTRA_SCRIPT_URI, getString(R.string.cm_wizard_script_user_uri));
            }
            startActivity(intent);
            finish();
        }
    }
}
