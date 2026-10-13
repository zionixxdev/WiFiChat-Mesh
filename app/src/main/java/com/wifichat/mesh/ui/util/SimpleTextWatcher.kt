package com.wifichat.mesh.ui.util

import android.text.Editable
import android.text.TextWatcher

/** Small helper so fragments can observe search input with a lambda. */
class SimpleTextWatcher(private val onChanged: (String) -> Unit) : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: Editable?) = onChanged(s?.toString().orEmpty())
}
