package com.playstudio.aiteacher

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

open class BaseFragment : Fragment() {

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Ensure the font is applied when fragment is attached
        FontManager.applySelectedFont(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return super.onCreateView(inflater, container, savedInstanceState)?.also { view ->
            FontManager.applyFontToView(requireContext(), view)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Additional logic after view creation
        FontManager.applyFontToView(requireContext(), view)
    }
}