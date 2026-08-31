package rew.lightgames.sudoku2

import android.content.Context
import android.content.Intent
import android.os.Bundle

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.fragment.app.Fragment

import androidx.navigation.fragment.findNavController



class FirstFragment : Fragment() {
    private fun playPop() {

        // TODO: 13/05/2023  
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.main_menu_fragment, container, false)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mPrefs = requireActivity().getSharedPreferences(MainActivity.PREF_NAME, Context.MODE_PRIVATE)


        if (!mPrefs.contains("saved_game")) {
            requireView().findViewById<View>(R.id.Resume).visibility = View.GONE
        } else {
            requireView().findViewById<View>(R.id.Resume).visibility = View.VISIBLE
        }
        view.findViewById<View>(R.id.Start).setOnClickListener { view1: View? ->
            playPop()
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }

        view.findViewById<View>(R.id.Resume).setOnClickListener { v: View? ->
            playPop()
            val i = Intent(activity, MainActivity::class.java)
            i.putExtra("Resume", true)
            startActivity(i)
        }
        view.findViewById<View>(R.id.OptnBttn).setOnClickListener { v: View? ->
            playPop()
            val i = Intent(activity, OptionsActivity::class.java)
            startActivity(i)
        }
        applyButtonSemantics(
            view.findViewById(R.id.Start),
            view.findViewById(R.id.Resume),
            view.findViewById(R.id.OptnBttn)
        )
    }

    private fun applyButtonSemantics(vararg views: View) {
        views.forEach { menuAction ->
            ViewCompat.setAccessibilityDelegate(
                menuAction,
                object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfoCompat
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = Button::class.java.name
                    }
                }
            )
        }
    }
}
