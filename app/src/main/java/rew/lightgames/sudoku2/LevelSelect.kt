    package rew.lightgames.sudoku2

    import android.content.Intent
    import android.os.Bundle
    import android.view.LayoutInflater
    import android.view.View
    import android.view.ViewGroup
    import androidx.fragment.app.Fragment
    import androidx.navigation.fragment.findNavController

    class LevelSelect : Fragment() {

        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            // Inflate the layout for this fragment
            return inflater.inflate(R.layout.fragment_second, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            // Easy button click listener
            view.findViewById<View>(R.id.Easy).setOnClickListener {
                val intent = Intent(activity, MainActivity::class.java)
                intent.putExtra("difficulty", "easy")
                start_game(intent)
            }

            // Medium button click listener
            view.findViewById<View>(R.id.Medium).setOnClickListener {
                val intent = Intent(activity, MainActivity::class.java)
                intent.putExtra("difficulty", "medium")
                start_game(intent)
            }

            // Hard button click listener
            view.findViewById<View>(R.id.HardBttn).setOnClickListener {
                val intent = Intent(activity, MainActivity::class.java)
                intent.putExtra("difficulty", "hard")
                start_game(intent)

            }

            // Previous button click listener
            view.findViewById<View>(R.id.button_second).setOnClickListener {
                findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
            }
        }

        private fun start_game(intent: Intent) {
            startActivity(intent)
            activity?.finish()
        }
    }
