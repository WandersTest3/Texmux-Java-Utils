package com.wanderstest3.termuxjavautils;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.wanderstest3.termuxjavautils.databinding.ActivityMainBinding;
import com.wanderstest3.termuxjavautils.ui.cicd.CicdActivity;
import com.wanderstest3.termuxjavautils.ui.scripts.ScriptsActivity;
import com.wanderstest3.termuxjavautils.ui.termux.TermuxActivity;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnOpenTermux.setOnClickListener(v ->
                startActivity(new Intent(this, TermuxActivity.class)));

        binding.btnOpenCicd.setOnClickListener(v ->
                startActivity(new Intent(this, CicdActivity.class)));

        binding.btnOpenScripts.setOnClickListener(v ->
                startActivity(new Intent(this, ScriptsActivity.class)));
    }
}
