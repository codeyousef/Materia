// VoxelCraft Web Worker entry point
// This file is processed by webpack to create the worker bundle

// Import the Kotlin/JS worker module
// The actual path will be resolved by webpack
importScripts('./voxelcraft.js');

// Initialize the worker from the Kotlin code
if (typeof io !== 'undefined' &&
    io.materia &&
    io.materia.examples &&
    io.materia.examples.voxelcraft &&
    io.materia.examples.voxelcraft.initMeshWorker) {

    io.materia.examples.voxelcraft.initMeshWorker();
} else {
    console.error('Worker: Failed to find initMeshWorker function');
}