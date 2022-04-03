package com.nextcloud.talk.ui.dialog

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.talk.databinding.DialogMessageReactionsBinding

class ShowReactionsDialog(
    val activity: Activity
) : BottomSheetDialog(activity) {

    private lateinit var dialogMessageReactionsBinding: DialogMessageReactionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialogMessageReactionsBinding = DialogMessageReactionsBinding.inflate(layoutInflater)
        setContentView(dialogMessageReactionsBinding.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
