from flask import Flask, request
import os
import subprocess

app = Flask(__name__)

@app.route('/webhook-handler', methods=['POST'])
def webhook_handler():
    if request.method == 'POST':
        data = request.json
        # Verify the payload, if needed (you can add secret handling here)
        
        # Pull the latest code from the repository
        repo_path = '/home/ubuntu/client'
        os.chdir(repo_path)
        subprocess.run(['git', 'pull'])

        # Run your script
        subprocess.run(['/home/ubuntu/client/rebuild.sh'])

        return 'Success', 200
    else:
        return 'Not allowed', 405

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
